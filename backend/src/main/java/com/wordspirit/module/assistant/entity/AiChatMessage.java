package com.wordspirit.module.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 消息实体：原始 JSON 数据 + 渲染预览文本
 */
@Data
@TableName("ai_chat_message")
public class AiChatMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long userId;
    /** user/assistant */
    private String role;
    /** 用户输入原文，或 AI 消息的渲染预览文本（纯文本兜底） */
    private String content;
    /** AI 原始 JSON 数据（供导入业务库/回看，前端不可直接暴露） */
    private String jsonData;
    /** 渲染类型: text/cards/paper/reading/refuse */
    private String renderType;
    private Integer isFavorited;
    private LocalDateTime createdAt;
}
