package com.wordspirit.module.explain.service;

import com.wordspirit.module.explain.dto.ExplainReq;
import com.wordspirit.module.explain.dto.ExplainResp;

/**
 * 阅读助手（模式A：就地调用）服务
 */
public interface WordExplainAiService {

    /** 解析英文文本（AI结构化输出 + 持久化缓存 + Redis热点缓存） */
    ExplainResp explain(Long userId, ExplainReq req);
}
