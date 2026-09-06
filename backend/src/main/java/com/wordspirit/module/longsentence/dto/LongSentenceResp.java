package com.wordspirit.module.longsentence.dto;

import lombok.Data;

/**
 * 长难句分析响应
 */
@Data
public class LongSentenceResp {

    /** 原文（被截断/规范化后的句子，前端直接渲染） */
    private String sentence;

    /** AI 完整分析结果 JSON 字符串（前端按字段解析渲染） */
    private String analysisJson;

    /** 是否来自缓存（true=命中 Redis/MySQL 缓存，未扣额度） */
    private Boolean fromCache;

    /** 本次分析后剩余的今日 AI 额度 */
    private Integer quotaRemain;

    /** 难度 easy/medium/hard（从 analysisJson 中抽出，便于列表/筛选） */
    private String difficulty;
}