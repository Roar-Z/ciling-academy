package com.wordspirit.module.longsentence.service;

import com.wordspirit.module.longsentence.dto.LongSentenceReq;
import com.wordspirit.module.longsentence.dto.LongSentenceResp;

/**
 * 长难句分析服务
 */
public interface LongSentenceService {

    /**
     * 分析英文长难句
     * <p>调用模式与 {@link com.wordspirit.module.explain.service.WordExplainAiService} 一致：
     * Redis 热点缓存 → MySQL 持久化缓存 → 调词灵AI（自动扣额度、自动 refund） → 双写缓存。</p>
     */
    LongSentenceResp analyze(Long userId, LongSentenceReq req);
}