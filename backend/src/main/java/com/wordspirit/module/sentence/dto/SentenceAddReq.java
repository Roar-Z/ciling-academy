package com.wordspirit.module.sentence.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入句灵集请求
 */
@Data
public class SentenceAddReq {

    @NotBlank(message = "原文不能为空")
    private String originalText;

    @NotBlank(message = "译文不能为空")
    private String translationText;

    /** en2zh | zh2en */
    @NotBlank(message = "翻译方向不能为空")
    private String direction;

    /** normal | ai */
    @NotBlank(message = "翻译模式不能为空")
    private String mode;

    /** 来源：translate | long_sentence，默认 translate */
    private String source;

    /** API 整句翻译快照（可选，普通翻译时为免费引擎整句，优先于 translationText 展示） */
    private String sentenceTranslation;

    /** 词卡快照（前端 JSON 字符串，可选） */
    private String normalWords;

    /** 长难句分析快照 JSON（可选，source=long_sentence 时使用） */
    private String analysisJson;
}