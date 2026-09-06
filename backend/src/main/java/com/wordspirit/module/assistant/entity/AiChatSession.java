package com.wordspirit.module.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话实体
 */
@Data
@TableName("ai_chat_session")
public class AiChatSession implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 会话标题 */
    private String title;
    /** 模式: chat/word_review/paper */
    private String mode;
    /** 来源标题（从哪个业务页面跳转） */
    private String sourceTitle;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
}
