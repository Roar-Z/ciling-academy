package com.wordspirit.module.longsentence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 长难句分析持久化缓存实体
 * <p>同一用户 + 同一句 MD5 仅保存一条，避免重复调用 AI。</p>
 */
@Data
@TableName("ai_long_sentence")
public class AiLongSentenceAnalysis implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 原文 MD5，与 userId 联合唯一 */
    private String contentHash;

    /** 原文前 80 字预览 */
    private String textPreview;

    /** easy/medium/hard */
    private String difficulty;

    /** AI 返回的完整分析 JSON */
    private String analysisJson;

    private LocalDateTime createdAt;
}