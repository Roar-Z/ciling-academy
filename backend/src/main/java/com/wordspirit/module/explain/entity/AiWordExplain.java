package com.wordspirit.module.explain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 阅读解析缓存实体（同一文本只解析一次，持久化缓存）
 */
@Data
@TableName("ai_word_explain")
public class AiWordExplain implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 原文MD5，幂等缓存 */
    private String contentHash;
    private String textPreview;
    /** easy/medium/hard */
    private String difficulty;
    /** 解析结果JSON */
    private String explainJson;
    private LocalDateTime createdAt;
}
