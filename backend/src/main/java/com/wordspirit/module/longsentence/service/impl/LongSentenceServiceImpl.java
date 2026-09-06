package com.wordspirit.module.longsentence.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wordspirit.ai.AiPromptConstant;
import com.wordspirit.ai.AiQuotaService;
import com.wordspirit.ai.AiService;
import com.wordspirit.module.longsentence.dto.LongSentenceReq;
import com.wordspirit.module.longsentence.dto.LongSentenceResp;
import com.wordspirit.module.longsentence.entity.AiLongSentenceAnalysis;
import com.wordspirit.module.longsentence.mapper.AiLongSentenceAnalysisMapper;
import com.wordspirit.module.longsentence.service.LongSentenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 长难句分析服务实现
 *
 * <p>缓存策略：</p>
 * <ul>
 *   <li>Redis 热点缓存（24h，key = ai:longsentence:{userId}:{md5}）</li>
 *   <li>MySQL 持久化缓存（ai_long_sentence，user_id + content_hash 唯一）</li>
 *   <li>命中缓存：不消耗 AI 额度</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LongSentenceServiceImpl implements LongSentenceService {

    /** Redis 热点缓存 key：用户级，避免不同用户串味 */
    private static final String CACHE_KEY = "ai:longsentence:{}:{}";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final AiService aiService;
    private final AiQuotaService quotaService;
    private final AiLongSentenceAnalysisMapper analysisMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public LongSentenceResp analyze(Long userId, LongSentenceReq req) {
        String sentence = req.getSentence().trim();
        if (sentence.length() > 500) {
            sentence = sentence.substring(0, 500);
        }
        String hash = SecureUtil.md5(sentence);
        String cacheKey = cn.hutool.core.text.StrFormatter.format(CACHE_KEY, userId, hash);

        // 1) Redis 热点缓存
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return buildResp(sentence, cached.toString(), true, quotaService.getTodayRemain(userId));
        }

        // 2) MySQL 持久化缓存
        AiLongSentenceAnalysis exist = analysisMapper.selectOne(new LambdaQueryWrapper<AiLongSentenceAnalysis>()
                .eq(AiLongSentenceAnalysis::getUserId, userId)
                .eq(AiLongSentenceAnalysis::getContentHash, hash));
        if (exist != null && StrUtil.isNotBlank(exist.getAnalysisJson())) {
            redisTemplate.opsForValue().set(cacheKey, exist.getAnalysisJson(), CACHE_TTL);
            return buildResp(sentence, exist.getAnalysisJson(), true, quotaService.getTodayRemain(userId));
        }

        // 3) 调词灵AI（额度/限流/缓存/失败退还 内部完成）
        String system = AiPromptConstant.GLOBAL_AI_PREFIX + AiPromptConstant.AI_LONG_SENTENCE;
        system = system.replace("{user_sentence}", sentence);
        String userPrompt = "请分析这个英文长难句：\n" + sentence;
        JSONObject json = aiService.callJson(userId, system, userPrompt, false);

        // 给 AI 返回补 sentence 字段，便于前端稳定渲染
        json.set("sentence", sentence);
        String jsonStr = json.toString();

        // 4) 双写缓存
        AiLongSentenceAnalysis record = new AiLongSentenceAnalysis();
        record.setUserId(userId);
        record.setContentHash(hash);
        record.setTextPreview(StrUtil.sub(sentence, 0, 80));
        record.setDifficulty(json.getStr("difficulty", ""));
        record.setAnalysisJson(jsonStr);
        try {
            analysisMapper.insert(record);
        } catch (Exception e) {
            log.warn("长难句分析缓存写入失败: {}", e.getMessage());
        }
        redisTemplate.opsForValue().set(cacheKey, jsonStr, CACHE_TTL);

        // 调用后再查一次剩余额度（callJson 内部失败已 refund，成功则扣减）
        return buildResp(sentence, jsonStr, false, quotaService.getTodayRemain(userId));
    }

    private LongSentenceResp buildResp(String sentence, String json, Boolean fromCache, int quotaRemain) {
        LongSentenceResp resp = new LongSentenceResp();
        resp.setSentence(sentence);
        resp.setAnalysisJson(json);
        resp.setFromCache(fromCache);
        resp.setQuotaRemain(quotaRemain);
        try {
            JSONObject obj = new JSONObject(json);
            resp.setDifficulty(obj.getStr("difficulty", ""));
        } catch (Exception ignored) {
            resp.setDifficulty("");
        }
        return resp;
    }
}