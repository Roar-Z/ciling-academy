package com.wordspirit.module.translate.dto;

import lombok.Data;

import java.util.List;

/**
 * 翻译响应
 * <ul>
 *   <li>普通翻译：normalWords 为词库逐词解析；translation 为整句直译；aiJson 为空</li>
 *   <li>AI 翻译：normalWords 为空；aiJson 为大模型返回的 JSON 字符串（前端解析）</li>
 * </ul>
 */
@Data
public class TranslateResp {

    /** normal | ai */
    private String mode;

    /** zh | en */
    private String srcLang;

    /** zh | en */
    private String tgtLang;

    /** 原文 */
    private String text;

    /** 普通翻译：整句直译（词对词拼接，用于收藏） */
    private String translation;

    /** 普通翻译：整句翻译（免费翻译引擎兜底，可能为 null，降级回 translation） */
    private String sentenceTranslation;

    /** 普通翻译：基于词库的逐词解析列表 */
    private List<NormalWord> normalWords;

    /** AI 翻译的 JSON 字符串（前端按方向解析） */
    private String aiJson;

    /** AI 翻译是否来自缓存 */
    private boolean fromCache;

    /** AI 翻译调用后的剩余额度（普通翻译恒为 null） */
    private Integer quotaRemain;
}