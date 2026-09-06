package com.wordspirit.module.explain.dto;

import lombok.Data;

import java.util.List;

/**
 * 阅读解析响应（模式A）
 */
@Data
public class ExplainResp {

    /** 解析结果 JSON（前端渲染层使用，用户不可见原始JSON） */
    private String explainJson;
    /** 是否命中缓存 */
    private Boolean fromCache;
    /** 重点词汇（供前端调用生词本高亮） */
    private List<String> keyWords;
    /** 剩余 AI 额度（前端 Toast 用） */
    private Integer quotaRemain;
}
