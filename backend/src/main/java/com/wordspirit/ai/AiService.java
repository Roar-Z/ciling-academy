package com.wordspirit.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wordspirit.common.ResultCode;
import com.wordspirit.config.AiGlobalConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 词灵AI 核心调用服务
 *
 * 通过 Hutool HTTP 直接调用阿里云百炼（DashScope）的 OpenAI 兼容接口，
 * 完全不依赖 Spring AI，避免版本/jar 下载问题。
 *
 * 标准调用流程：
 * 登录校验 → 额度校验(AiQuotaService) → 限流(AiRateLimitService)
 * → 缓存查询(Redis) → 未命中则 RAG 检索 → 调用千问 → JSON 清洗校验 → 写入缓存
 *
 * 关键设计：
 * - 全局缓存：相同输入只调用一次大模型
 * - 超时熔断：CompletableFuture.get(timeout) 实现硬超时
 * - 重试：按 retry-times 自动重试
 * - ai.enable=false：直接抛出 AI_UNAVAILABLE，业务层优雅降级
 * - 深度思考/联网搜索：仅切换模型与开关参数，仍是单次调用（见 callJson 额度说明）
 */
@Slf4j
@Service
public class AiService {

    private static final String CACHE_KEY = "ai:cache:{}";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    /** 响应 JSON 中承载思维链的字段名（供前端「查看思考过程」读取，不影响现有渲染结构） */
    private static final String REASONING_FIELD = "reasoning";

    /** 响应 JSON 中承载本端附加 meta 的字段名（前端专有：deepThink / webSearch 开关状态） */
    private static final String CI_META_FIELD = "_ciMeta";

    /** 匹配 json代码围块 */
    private static final Pattern FENCE = Pattern.compile("^```(json)?\\s*|\\s*```$", Pattern.MULTILINE);

    private final AiGlobalConfig aiConfig;
    private final AiQuotaService quotaService;
    private final AiRateLimitService rateLimitService;
    private final RagSearchService ragSearchService;
    private final RedisTemplate<String, Object> redisTemplate;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public AiService(AiGlobalConfig aiConfig, AiQuotaService quotaService,
                     AiRateLimitService rateLimitService, RagSearchService ragSearchService,
                     RedisTemplate<String, Object> redisTemplate) {
        this.aiConfig = aiConfig;
        this.quotaService = quotaService;
        this.rateLimitService = rateLimitService;
        this.ragSearchService = ragSearchService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 调用词灵AI，返回结构化 JSON 对象（已清洗校验）
     *
     * <p>扣减策略：</p>
     * <ol>
     *   <li>限流（最前，挡住瞬时高频）</li>
     *   <li>全局缓存（命中不消耗额度）</li>
     *   <li>额度扣减</li>
     *   <li>RAG 检索 + 调用大模型 + JSON 校验（失败时 refund 退还额度）</li>
     * </ol>
     */
    public JSONObject callJson(Long userId, String system, String user, boolean useRag) {
        return callJson(userId, system, user, useRag, false, false);
    }

    /**
     * 调用词灵AI（支持深度思考 / 联网搜索），返回结构化 JSON 对象
     *
     * <p><b>额度说明（重要）</b>：开启深度思考 / 联网搜索会<b>额外消耗 1 次额度</b>
     * （基础 1 次 → 增强 2 次）；<b>两个开关同开不叠加</b>，仍为 2 次。
     * 增强模式虽仍是单次大模型 HTTP 调用，但使用了更贵的思考/联网模型，
     * 按产品规则计 2 次额度。额度扣减在本方法第 3 步，失败重试不额外扣减。</p>
     *
     * @param deepThink 深度思考：切换思考模型 + 开启 enable_thinking，响应携带 reasoning_content
     * @param webSearch 联网搜索：切换支持联网的模型 + 开启 enable_search
     */
    public JSONObject callJson(Long userId, String system, String user, boolean useRag,
                               boolean deepThink, boolean webSearch) {
        // 1. 系统QPS限流 + 用户冷却
        rateLimitService.checkAndMark(userId);

        // 2. 全局缓存：相同输入只调用一次大模型（命中不消耗额度）
        //    缓存键带上开关，否则开/关深度思考会错误命中同一份结果
        String cacheKey = cacheKey(system, user, deepThink, webSearch);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("词灵AI缓存命中: {}", cacheKey);
            return parseJson(cached.toString());
        }

        // 3. 额度校验并扣减
        //    计费规则见 calcCost：基础 1 次，实际启用增强能力额外 +1 次
        int cost = calcCost(deepThink, webSearch);
        quotaService.checkAndConsume(userId, cost);

        // 4. RAG 检索，注入参考知识点
        if (useRag) {
            String ragContext = String.join("\n", ragSearchService.searchTopK(extractQuery(user)));
            system = system.replace("{rag_context}", StrUtil.isNotBlank(ragContext) ? ragContext : "（无外部参考知识）");
        }

        // 5. 调用大模型 + JSON 校验（格式异常在重试循环内自动重试；失败/格式异常一律退还额度）
        JSONObject result;
        try {
            ParsedResult parsed = chatJson(system, user, 0.7, deepThink, webSearch);
            result = parsed.json();
            // 深度思考：把思维链一并返回，供前端「查看思考过程」读取（不影响现有渲染结构）
            if (StrUtil.isNotBlank(parsed.reasoning())) {
                result.set(REASONING_FIELD, parsed.reasoning());
            }
            // 附加本次调用的开关状态（_ciMeta），供前端展示「思考过程」「联网参考」等区域
            // 用下划线前缀避免与模型返回的业务 JSON key 冲突；不影响 detectRenderType / buildPreview
            result.set(CI_META_FIELD, JSONUtil.createObj()
                    .set("deepThink", deepThink)
                    .set("webSearch", webSearch));
        } catch (Exception e) {
            log.warn("词灵AI解析失败，退还额度 userId={} reason={}", userId, e.getMessage());
            try {
                quotaService.refund(userId, cost);
            } catch (Exception refundEx) {
                log.error("额度退还失败 userId={}", userId, refundEx);
            }
            throw e;
        }

        // 6. 写入热点缓存（存最终 JSON，命中缓存时也能拿到思维链）
        redisTemplate.opsForValue().set(cacheKey, result.toString(), CACHE_TTL);
        return result;
    }

    /**
     * 计算本次调用的额度消耗次数：
     * 基础 1 次；实际启用深度思考 / 联网搜索时额外 +1 次（<b>两者同开不叠加</b>，仍为 2 次）。
     *
     * <p>按「<b>实际生效</b>」的能力计费：若对应能力被配置关闭（降级开关为 false），
     * 本次并不会真的切换到增强模型，因此<b>不额外计费</b>，避免用户被多扣额度。</p>
     */
    public int calcCost(boolean deepThink, boolean webSearch) {
        boolean enhanced = (deepThink && aiConfig.getQwen().isThinkingEnabled())
                || (webSearch && aiConfig.getQwen().isSearchEnabled());
        return enhanced ? 2 : 1;
    }

    /**
     * 调用大模型并要求返回合法 JSON：清洗 + 解析都在重试循环<b>内部</b>完成。
     * 模型偶发输出截断（finish_reason=length）或格式不规范时自动重试，
     * 避免"一次格式异常就整个请求失败、白扣额度"。全部重试仍失败才抛异常。
     */
    private ParsedResult chatJson(String system, String user, double temperature,
                                  boolean deepThink, boolean webSearch) {
        if (!aiConfig.isEnable()) {
            throw new AiException(ResultCode.AI_UNAVAILABLE);
        }
        Exception lastError = null;
        boolean formatError = false;
        for (int i = 0; i <= aiConfig.getRetryTimes(); i++) {
            try {
                String finalSystem = system;
                CompletableFuture<RawResult> future = CompletableFuture.supplyAsync(() -> {
                    return doCallQwen(finalSystem, user, temperature, deepThink, webSearch);
                }, executor);
                RawResult raw = future.get(aiConfig.getTimeout(), TimeUnit.MILLISECONDS);
                if ("length".equals(raw.finishReason())) {
                    formatError = true;
                    throw new RuntimeException("AI输出被max_tokens截断，JSON不完整");
                }
                return new ParsedResult(parseJson(cleanJson(raw.content())), raw.reasoning());
            } catch (AiException e) {
                // 格式异常可重试；服务未启用等系统性错误直接抛出
                if (e.getCode() != ResultCode.AI_FORMAT_ERROR.getCode()) {
                    throw e;
                }
                lastError = e;
                formatError = true;
                log.warn("词灵AI输出格式异常 第{}次", i + 1);
            } catch (Exception e) {
                lastError = e;
                if (e.getMessage() != null && e.getMessage().contains("截断")) {
                    formatError = true;
                }
                log.warn("词灵AI调用失败 第{}次: {}", i + 1, e.getMessage());
            }
        }
        log.error("词灵AI调用最终失败", lastError);
        throw new AiException(formatError ? ResultCode.AI_FORMAT_ERROR : ResultCode.AI_UNAVAILABLE);
    }

    /**
     * 系统级调用大模型：<b>不扣减用户额度、不查全局缓存、不做用户限流</b>，
     * 仅复用重试/超时/清洗前的原始输出（不做 JSON 裁剪），返回完整模型内容。
     * 用于"句灵日选"等系统定时任务——避免占用用户 AI 额度、避免被全局缓存复用。
     *
     * @param temperature 温度（如 0.75），控制创意/稳健
     * @return 模型原始输出（未做 JSON fence 裁剪），调用方自行解析
     */
    public String rawChat(String system, String user, double temperature) {
        if (!aiConfig.isEnable()) {
            throw new AiException(ResultCode.AI_UNAVAILABLE);
        }
        Exception lastError = null;
        for (int i = 0; i <= aiConfig.getRetryTimes(); i++) {
            try {
                String finalSystem = system;
                CompletableFuture<RawResult> future = CompletableFuture.supplyAsync(() -> {
                    return doCallQwen(finalSystem, user, temperature, false, false);
                }, executor);
                return future.get(aiConfig.getTimeout(), TimeUnit.MILLISECONDS).content();
            } catch (Exception e) {
                lastError = e;
                log.warn("系统级AI调用失败 第{}次: {}", i + 1, e.getMessage());
            }
        }
        log.error("系统级AI调用最终失败", lastError);
        throw new AiException(ResultCode.AI_UNAVAILABLE);
    }

    /**
     * 直接用 Hutool HTTP 调用千问 OpenAI 兼容接口
     * POST {base-url}/chat/completions
     * Authorization: Bearer {api-key}
     * Body: { model, messages:[system+user], temperature, max_tokens[, enable_thinking][, enable_search] }
     *
     * <p>开关与模型的关系（始终单次调用）：</p>
     * <ul>
     *   <li>深度思考 → thinkingModel + enable_thinking=true（响应含 reasoning_content）</li>
     *   <li>联网搜索 → searchModel + enable_search=true</li>
     *   <li>两者同开 → 用 thinkingModel，同时带两个参数（仍是一次请求、一次计费）</li>
     *   <li>都不开 → 常规 model</li>
     * </ul>
     */
    private RawResult doCallQwen(String system, String user, double temperature,
                                 boolean deepThink, boolean webSearch) {
        String baseUrl = aiConfig.getQwen().getBaseUrl();
        String apiKey = aiConfig.getQwen().getApiKey();
        boolean useThinking = deepThink && aiConfig.getQwen().isThinkingEnabled();
        boolean useSearch = webSearch && aiConfig.getQwen().isSearchEnabled();

        // 模型选择优先级：深度思考 > 联网搜索 > 常规模型
        String model = aiConfig.getQwen().getModel();
        if (useThinking) {
            model = aiConfig.getQwen().getThinkingModel();
        } else if (useSearch) {
            model = aiConfig.getQwen().getSearchModel();
        }

        JSONArray messages = JSONUtil.createArray();
        messages.add(JSONUtil.createObj().set("role", "system").set("content", system));
        messages.add(JSONUtil.createObj().set("role", "user").set("content", user));

        JSONObject body = JSONUtil.createObj()
                .set("model", model)
                .set("messages", messages)
                .set("temperature", temperature)
                // 思维链会占用输出额度，深度思考时放宽上限；
                // 常规输出放宽到 4096，避免 word_review 等长 JSON 被截断导致解析失败
                .set("max_tokens", useThinking ? aiConfig.getQwen().getThinkingMaxTokens() : 4096);

        // 深度思考：开启思维链（百炼 qwen3 系列，响应携带 reasoning_content）
        if (useThinking) {
            body.set("enable_thinking", true);
        }
        // 联网搜索：使用 OpenAI 标准的 function calling 调用百炼内置 web_search 工具
        // 百炼 OpenAI 兼容接口不接受 enable_search 简单参数，必须走 tools 字段；
        // 百炼会自动处理 tool_calls 循环并把搜索结果融入最终 content
        if (useSearch) {
            body.set("tools", JSONUtil.parseArray("""
                    [
                      {
                        "type": "function",
                        "function": {
                            "name": "web_search",
                            "description": "联网搜索实时信息",
                            "parameters": {
                              "type": "object",
                              "properties": {
                                "query": {"type": "string", "description": "搜索关键词"}
                              },
                              "required": ["query"]
                            }
                          }
                      }
                    ]
                    """));
        }

        try (HttpResponse resp = HttpRequest.post(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout((int) aiConfig.getTimeout())
                .execute()) {
            if (!resp.isOk()) {
                throw new RuntimeException("HTTP " + resp.getStatus() + " " + truncate(resp.body(), 200));
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String content = message.getStr("content");
            // 深度思考模式下，思维链单独放在 reasoning_content
            String reasoning = message.getStr("reasoning_content");
            if (StrUtil.isBlank(content)) {
                throw new RuntimeException("AI响应内容为空");
            }
            // finish_reason=length 表示输出被 max_tokens 截断，JSON 必然不完整，交给上层重试
            return new RawResult(content, reasoning, choice.getStr("finish_reason"));
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 清洗模型输出：去掉围栏/前后缀，仅保留 JSON 片段 */
    private String cleanJson(String content) {
        if (StrUtil.isBlank(content)) {
            throw new AiException(ResultCode.AI_FORMAT_ERROR);
        }
        String text = content.trim();
        Matcher m = FENCE.matcher(text);
        if (m.find()) {
            text = text.replaceAll("(?s)^```(json)?\\s*|\\s*```$", "").trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiException(ResultCode.AI_FORMAT_ERROR);
        }
        return text.substring(start, end + 1);
    }

    /** JSON 解析（严格模式） */
    private JSONObject parseJson(String json) {
        try {
            JSONObject obj = JSONUtil.parseObj(json);
            if (obj == null) {
                throw new AiException(ResultCode.AI_FORMAT_ERROR);
            }
            return obj;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(ResultCode.AI_FORMAT_ERROR);
        }
    }

    /** 从用户输入中提取检索 query（截断避免超长） */
    private String extractQuery(String user) {
        return StrUtil.sub(user, 0, 200);
    }

    /** 缓存键：带上深度思考/联网搜索开关，避免不同参数错误命中同一份结果 */
    private String cacheKey(String system, String user, boolean deepThink, boolean webSearch) {
        String seed = system + user + "|think=" + deepThink + "|search=" + webSearch;
        return cn.hutool.core.text.StrFormatter.format(CACHE_KEY, SecureUtil.md5(seed));
    }

    /** 模型原始返回：正文 content + 思维链 reasoning（仅深度思考模式有值） */
    private record RawResult(String content, String reasoning, String finishReason) {}

    /** chatJson 的解析结果：合法 JSON + 思维链 */
    private record ParsedResult(JSONObject json, String reasoning) {}
}
