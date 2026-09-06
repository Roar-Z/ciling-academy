package com.wordspirit.module.translate.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiPromptConstant;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.ai.AiService;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.mapper.DictWordMapper;
import com.wordspirit.module.translate.dto.NormalWord;
import com.wordspirit.module.translate.dto.TranslateReq;
import com.wordspirit.module.translate.dto.TranslateResp;
import com.wordspirit.module.translate.service.TranslateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 翻译服务实现
 * <ul>
 *   <li>普通翻译：基于本地词库（dict_word）逐词释义，不消耗额度、不调外部 API</li>
 *   <li>AI 翻译：复用 AiService.callJson（自动扣额度、自动 refund）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslateServiceImpl implements TranslateService {

    /** 英文单词分词（仅取字母） */
    private static final Pattern WORD = Pattern.compile("[a-zA-Z]+");

    /**
     * 不做词形还原的短虚词（避免被规则误改）
     */
    private static final Set<String> SHORT_WORDS = Set.of(
            "is", "as", "us", "has", "its", "was", "his", "this", "thus", "yes",
            "gas", "bus", "hers", "ours", "theirs", "yours", "mine", "ms", "ss"
    );

    private final AiService aiService;
    private final AiQuotaService quotaService;
    private final DictWordMapper dictWordMapper;

    /** 可选 LibreTranslate 容器/实例地址（本地或云端皆可）；不配置/不可达时自动跳过，降级 MyMemory */
    @Value("${libretranslate.url:}")
    private String libreTranslateUrl;

    /** 可选 MyMemory 授权邮箱（免 key）；填后额度从匿名约 5000 词/天提升到约 50000 字符/天，需点击邮件确认 */
    @Value("${mymemory.email:}")
    private String myMemoryEmail;

    @Override
    public TranslateResp translate(Long userId, TranslateReq req) {
        String text = req.getText().trim();
        if (StrUtil.isBlank(text)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "原文不能为空");
        }

        TranslateResp resp = new TranslateResp();
        resp.setText(text);
        resp.setMode(req.getMode());

        if ("normal".equalsIgnoreCase(req.getMode())) {
            // 普通翻译根据输入自动判定方向（en→zh 走逐词解析；zh→en 走词库反查）
            doNormal(text, resp);
        } else if ("ai".equalsIgnoreCase(req.getMode())) {
            String src = normalizeLang(text);
            String tgt = "zh".equals(src) ? "en" : "zh";
            resp.setSrcLang(src);
            resp.setTgtLang(tgt);
            doAi(userId, text, src, tgt, resp);
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的翻译模式");
        }
        return resp;
    }

    // =================== 普通翻译（词库逐词解析） ===================

    /**
     * 把英文句子拆成词，去词库逐个查释义，拼出逐词解析 + 整句直译
     */
    private void doNormal(String text, TranslateResp resp) {
        // 中文输入 → 走词库 meaning 反查，给出相关英文词（基础翻译）
        if (containsChinese(text)) {
            resp.setSrcLang("zh");
            resp.setTgtLang("en");
            doNormalZh2En(text, resp);
            return;
        }
        // 英文输入 → 走词库逐词解析
        resp.setSrcLang("en");
        resp.setTgtLang("zh");
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "普通翻译基于英文词库，未识别到英文单词，请输入英文或切换到「词灵AI 翻译」");
        }
        // 1) 给每个 token 收集可能的原形（含本身），一次性批量查词库
        Set<String> lookup = new LinkedHashSet<>();
        for (String tk : tokens) {
            lookup.addAll(stems(tk));
        }
        List<DictWord> dictWords = dictWordMapper.selectList(
                new LambdaQueryWrapper<DictWord>().in(DictWord::getWord, lookup));
        Map<String, DictWord> dictMap = dictWords.stream()
                .collect(Collectors.toMap(DictWord::getWord, w -> w, (a, b) -> a));

        // 2) 组装逐词解析：每个 token 按 stems 顺序找首个命中
        List<NormalWord> normalWords = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String tk : tokens) {
            NormalWord nw = new NormalWord();
            nw.setWord(tk);
            DictWord dw = null;
            String used = tk;
            for (String s : stems(tk)) {
                DictWord hit = dictMap.get(s);
                if (hit != null) {
                    dw = hit;
                    used = s;
                    break;
                }
            }
            if (dw != null) {
                nw.setFound(true);
                nw.setPhonetic(dw.getPhonetic());
                nw.setPos(dw.getPos());
                String suffix = used.equals(tk) ? "" : " (原形:" + used + ")";
                String meaning = StrUtil.isBlank(dw.getMeaning()) ? tk : dw.getMeaning();
                nw.setMeaning(meaning + suffix);
                // 整句直译用清理后的首段释义（去词性、取第一义），避免 "n. 狗, 犬, 犬科动物"
                String clean = cleanMeaning(dw.getMeaning());
                sb.append(StrUtil.isBlank(clean) ? tk : clean).append(" ");
            } else {
                nw.setFound(false);
                nw.setMeaning("");
                sb.append(tk).append(" ");
            }
            normalWords.add(nw);
        }
        resp.setNormalWords(normalWords);
        resp.setTranslation(sb.toString().trim());
        // 免费整句翻译兜底（不花千问额度，失败自动降级到词库拼接）
        resp.setSentenceTranslation(freeTranslate(text, "en|zh-CN"));
    }

    /**
     * 英文词形还原候选（含原词），按优先级返回
     * 规则：-s/-es（复数/三单）、-ies→-y、-ed（过去式）、-ing（现在分词）
     * 短虚词（is/as/has...）一律不还原，避免误伤
     */
    private List<String> stems(String token) {
        List<String> out = new ArrayList<>();
        out.add(token);
        if (token.length() < 3) {
            return out;
        }
        if (SHORT_WORDS.contains(token)) {
            return out;
        }
        // ies → y (cities -> city)
        if (token.endsWith("ies") && token.length() > 4) {
            out.add(token.substring(0, token.length() - 3) + "y");
        }
        // -ses / -xes / -zes / -ches / -shes → 去 es (watches -> watch)
        if (token.endsWith("ses") || token.endsWith("xes") || token.endsWith("zes")
                || token.endsWith("ches") || token.endsWith("shes")) {
            out.add(token.substring(0, token.length() - 2));
        } else if (token.endsWith("s") && !token.endsWith("ss") && !token.endsWith("us")) {
            // 复数 / 三单：jumps -> jump
            out.add(token.substring(0, token.length() - 1));
        }
        // -ed：jumped -> jump, hoped -> hope
        if (token.endsWith("ed") && token.length() > 4) {
            String base = token.substring(0, token.length() - 2);
            out.add(base);
            if (!base.endsWith("e")) {
                out.add(base + "e");
            }
        }
        // -ing：jumping -> jump, hoping -> hope
        if (token.endsWith("ing") && token.length() > 5) {
            String base = token.substring(0, token.length() - 3);
            out.add(base);
            if (!base.endsWith("e")) {
                out.add(base + "e");
            }
        }
        return out.stream().distinct().collect(Collectors.toList());
    }

    /** 英文分词：仅取字母，转小写，保序去重 */
    private List<String> tokenize(String text) {
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = WORD.matcher(text.toLowerCase());
        while (m.find()) {
            String w = m.group();
            if (w.length() >= 1) {
                seen.add(w);
            }
        }
        return new ArrayList<>(seen);
    }

    // =================== AI 翻译 ===================

    private void doAi(Long userId, String text, String src, String tgt, TranslateResp resp) {
        String system = AiPromptConstant.GLOBAL_AI_PREFIX + AiPromptConstant.AI_TRANSLATE;
        String direction = "zh".equals(src) && "en".equals(tgt) ? "zh2en" : "en2zh";
        String userPrompt = "direction=" + direction + "\n原文：\n" + text;
        int before = quotaService.getTodayRemain(userId);
        try {
            JSONObject json = aiService.callJson(userId, system, userPrompt, false);
            // 给 AI 返回补一个 direction 字段，便于前端稳定渲染
            json.set("direction", direction);
            resp.setAiJson(json.toString());
            resp.setQuotaRemain(quotaService.getTodayRemain(userId));
            resp.setFromCache(before == resp.getQuotaRemain());
        } catch (Exception e) {
            log.warn("AI 翻译失败 userId={} reason={}", userId, e.getMessage());
            throw e;
        }
    }

    // =================== 工具 ===================

    /** 自动识别原文语种：包含中文字符判 zh，否则 en */
    private String normalizeLang(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return "zh";
            }
        }
        return "en";
    }

    /** 是否包含中文字符 */
    private boolean containsChinese(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                return true;
            }
        }
        return false;
    }

    /**
     * 普通翻译 - 中文输入：对整段文本做 meaning 模糊查，反查词库相关英文词
     * 注意：这是"基础反查"，非真正翻译；完整中→英请用 AI 翻译
     */
    private void doNormalZh2En(String text, TranslateResp resp) {
        String trimmed = text.trim();
        // 截断避免 like 太长影响性能
        String q = trimmed.length() > 30 ? trimmed.substring(0, 30) : trimmed;
        List<DictWord> hits = dictWordMapper.selectList(
                new LambdaQueryWrapper<DictWord>()
                        .like(DictWord::getMeaning, q)
                        .orderByDesc(DictWord::getIsCore)
                        .orderByAsc(DictWord::getWord)
                        .last("LIMIT 30"));
        List<NormalWord> nws = hits.stream().map(dw -> {
            NormalWord nw = new NormalWord();
            nw.setWord(dw.getWord());
            nw.setPhonetic(dw.getPhonetic());
            nw.setPos(dw.getPos());
            nw.setMeaning(dw.getMeaning());
            nw.setFound(true);
            return nw;
        }).collect(Collectors.toList());
        resp.setNormalWords(nws);
        resp.setTranslation("");
    }

    /**
     * 清理单词释义：去词性前缀、只取第一义
     * 用于整句直译拼接，避免出现 "n. 狗, 犬, 犬科动物" 这种串
     */
    private String cleanMeaning(String m) {
        if (StrUtil.isBlank(m)) {
            return "";
        }
        String s = m.trim();
        // 去词性前缀（n./v./adj./prep./art./aux./conj./adv./pron./num./interj./vi./vt.）
        s = s.replaceFirst("^(n|v|adj|adv|prep|conj|art|pron|aux|num|interj|int|vi|vt)\\.\\s*", "");
        // 按常见分隔符取第一段
        String[] seps = {";", "；", ", ", "，"};
        int idx = -1;
        for (String sep : seps) {
            int p = s.indexOf(sep);
            if (p > 0 && (idx < 0 || p < idx)) {
                idx = p;
            }
        }
        if (idx > 0) {
            s = s.substring(0, idx);
        }
        return s.trim();
    }

    /**
     * 免费整句翻译兜底（不花千问额度，无需 API key）：
     * 1) 优先 MyMemory 公开翻译 API（免 key，约 5000 词/天免费，质量稳定）
     * 2) 失败降级本地/云端 LibreTranslate（若配置了 libretranslate.url 且可达）
     * 3) 再失败返回 null，前端降级到词库拼接
     */
    private String freeTranslate(String text, String langpair) {
        if (StrUtil.isBlank(text) || text.length() > 500) {
            return null;
        }
        // 主用免 key 公共 API，不依赖本地 Docker
        String r = myMemory(text, langpair);
        if (StrUtil.isNotBlank(r)) {
            return r;
        }
        String[] parts = langpair.split("\\|");
        String src = parts.length > 0 ? parts[0] : "en";
        String tgt = parts.length > 1 ? parts[1] : "zh-CN";
        return libreTranslate(text, src, tgt);
    }

    /** 本地 LibreTranslate 容器（POST /translate，JSON） */
    private String libreTranslate(String text, String src, String tgt) {
        if (StrUtil.isBlank(libreTranslateUrl)) {
            return null;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            // 用 JSONUtil.toJsonStr 安全转义，避免引号破坏 JSON
            String payload = "{\"q\":" + JSONUtil.toJsonStr(text)
                    + ",\"source\":\"" + src + "\",\"target\":\"" + tgt + "\",\"format\":\"text\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(libreTranslateUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || StrUtil.isBlank(resp.body())) {
                return null;
            }
            JSONObject json = JSONUtil.parseObj(resp.body());
            String t = json.getStr("translatedText");
            if (StrUtil.isBlank(t)) {
                return null;
            }
            String low = t.toLowerCase();
            if (low.contains("error") || low.contains("mymemory")) {
                return null;
            }
            return t.trim();
        } catch (Exception e) {
            log.warn("libretranslate failed: {}", e.getMessage());
            return null;
        }
    }

    /** MyMemory 公开翻译 API（无需 key，约 5000 词/天免费；配授权邮箱后约 50000 字符/天） */
    private String myMemory(String text, String langpair) {
        try {
            // mt=1：无记忆匹配时回退机器翻译，保证返回完整通顺整句
            // langpair 中的 '|' 必须 URL 编码（→ %7C），否则 URI.create 报 Illegal character
            String langEnc = URLEncoder.encode(langpair, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder("https://api.mymemory.translated.net/get?q=")
                    .append(URLEncoder.encode(text, StandardCharsets.UTF_8))
                    .append("&langpair=").append(langEnc).append("&mt=1");
            // 授权邮箱：MyMemory 会向你邮箱发确认邮件，点击链接授权后额度提升（仍然免 key）
            if (StrUtil.isNotBlank(myMemoryEmail)) {
                sb.append("&de=").append(URLEncoder.encode(myMemoryEmail, StandardCharsets.UTF_8));
            }
            String url = sb.toString();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> httpResp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResp.statusCode() != 200 || StrUtil.isBlank(httpResp.body())) {
                return null;
            }
            JSONObject json = JSONUtil.parseObj(httpResp.body());
            JSONObject rd = json.getJSONObject("responseData");
            if (rd == null) {
                return null;
            }
            String t = rd.getStr("translatedText");
            if (StrUtil.isBlank(t)) {
                return null;
            }
            String low = t.toLowerCase();
            // 超额度 / 异常时 MyMemory 会返回带 MYMEMORY / WARNING / QUOTA 的提示
            if (low.contains("mymemory") || low.contains("warning") || low.contains("quota")) {
                return null;
            }
            return t.trim();
        } catch (Exception e) {
            log.warn("mymemory failed: {}", e.getMessage());
            return null;
        }
    }
}