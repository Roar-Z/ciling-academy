package com.wordspirit.module.explain.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiPromptConstant;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.ai.AiService;
import com.wordspirit.module.explain.dto.ExplainReq;
import com.wordspirit.module.explain.dto.ExplainResp;
import com.wordspirit.module.explain.entity.AiWordExplain;
import com.wordspirit.module.explain.mapper.AiWordExplainMapper;
import com.wordspirit.module.explain.service.WordExplainAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 阅读助手服务实现
 *
 * 模式A特点：当前页面直接调用接口，弹窗展示结果；不进入对话历史；完整权限、额度、缓存、降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WordExplainAiServiceImpl implements WordExplainAiService {

    private static final String CACHE_KEY = "ai:explain:{}:{}";
    private static final Duration CACHE_TTL = Duration.ofDays(3);

    private final AiService aiService;
    private final AiQuotaService quotaService;
    private final AiWordExplainMapper explainMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public ExplainResp explain(Long userId, ExplainReq req) {
        String text = req.getText().trim();
        String hash = SecureUtil.md5(text);

        // 1. Redis 热点缓存
        String cacheKey = cn.hutool.core.text.StrFormatter.format(CACHE_KEY, userId, hash);
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return buildResp(cached.toString(), true, quotaService.getTodayRemain(userId));
        }

        // 2. MySQL 持久化缓存（同一文本只调用一次大模型）
        AiWordExplain exist = explainMapper.selectOne(new LambdaQueryWrapper<AiWordExplain>()
                .eq(AiWordExplain::getUserId, userId)
                .eq(AiWordExplain::getContentHash, hash));
        if (exist != null && StrUtil.isNotBlank(exist.getExplainJson())) {
            redisTemplate.opsForValue().set(cacheKey, exist.getExplainJson(), CACHE_TTL);
            return buildResp(exist.getExplainJson(), true, quotaService.getTodayRemain(userId));
        }

        // 3. 调用词灵AI（额度/限流/缓存/失败退还 内部完成）
        String system = AiPromptConstant.GLOBAL_AI_PREFIX + AiPromptConstant.AI_READING_EXPLAIN;
        system = system.replace("{user_text}", StrUtil.sub(text, 0, 3000));
        JSONObject json = aiService.callJson(userId, system, "请解析以下英文文本：\n" + StrUtil.sub(text, 0, 3000), false);

        // 4. 持久化缓存 + Redis 热点缓存
        AiWordExplain record = new AiWordExplain();
        record.setUserId(userId);
        record.setContentHash(hash);
        record.setTextPreview(StrUtil.sub(text, 0, 80));
        record.setDifficulty(json.getStr("difficulty", ""));
        record.setExplainJson(json.toString());
        try {
            explainMapper.insert(record);
        } catch (Exception e) {
            log.warn("阅读解析缓存写入失败: {}", e.getMessage());
        }
        redisTemplate.opsForValue().set(cacheKey, json.toString(), CACHE_TTL);

        // 调用后再查一次剩余额度（callJson 内部失败会 refund，成功则扣减）
        return buildResp(json.toString(), false, quotaService.getTodayRemain(userId));
    }

    /** 组装响应并抽取重点词汇 */
    private ExplainResp buildResp(String json, boolean fromCache, int quotaRemain) {
        ExplainResp resp = new ExplainResp();
        resp.setExplainJson(json);
        resp.setFromCache(fromCache);
        resp.setQuotaRemain(quotaRemain);
        try {
            JSONObject obj = new JSONObject(json);
            // 保留顺序 + 去重：先 key_words，再从 sentences[].hard_points[].word 兜底补齐
            Set<String> seen = new LinkedHashSet<>();
            // 1) key_words（AI 显式声明的重点词）
            JSONArray kws = obj.getJSONArray("key_words");
            if (kws != null) {
                for (int i = 0; i < kws.size(); i++) {
                    String w = kws.getStr(i);
                    if (StrUtil.isNotBlank(w)) seen.add(w.trim());
                }
            }
            // 2) sentences[].hard_points[].word（页面所有高亮词）
            JSONArray sentences = obj.getJSONArray("sentences");
            if (sentences != null) {
                for (int i = 0; i < sentences.size(); i++) {
                    JSONObject s = sentences.getJSONObject(i);
                    if (s == null) continue;
                    JSONArray points = s.getJSONArray("hard_points");
                    if (points == null) continue;
                    for (int j = 0; j < points.size(); j++) {
                        JSONObject p = points.getJSONObject(j);
                        if (p == null) continue;
                        String w = p.getStr("word");
                        if (StrUtil.isNotBlank(w)) seen.add(w.trim());
                    }
                }
            }
            resp.setKeyWords(new ArrayList<>(seen));
        } catch (Exception ignored) {
            resp.setKeyWords(List.of());
        }
        return resp;
    }
}
