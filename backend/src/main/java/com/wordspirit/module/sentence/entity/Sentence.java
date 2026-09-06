package com.wordspirit.module.sentence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 句灵集实体（用户收藏的翻译句子）
 */
@Data
@TableName("sentence")
public class Sentence implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 原文 */
    private String originalText;
    /** 译文 */
    private String translationText;
    /** en2zh | zh2en */
    private String direction;
    /** normal | ai */
    private String mode;
    /** translate(翻译助手) */
    private String source = "translate";
    /** API 整句翻译快照（可选，句灵集离线展示用） */
    private String sentenceTranslation;
    /** 词卡快照 JSON（可选，句灵集离线展示用） */
    private String normalWords;
    /** 长难句分析 JSON 快照（source=long_sentence 时使用，可选） */
    private String analysisJson;
    private LocalDateTime createdAt;
}