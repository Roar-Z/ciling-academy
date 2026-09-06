package com.wordspirit.module.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 收藏实体（我的AI笔记）
 */
@Data
@TableName("ai_favorite")
public class AiFavorite implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long sessionId;
    private Long messageId;
    private String title;
    /** 渲染预览文本 */
    private String content;
    /** 原始 JSON 数据 */
    private String jsonData;
    private String renderType;
    private LocalDateTime createdAt;
}
