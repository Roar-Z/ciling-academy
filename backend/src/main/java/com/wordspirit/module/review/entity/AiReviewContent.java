package com.wordspirit.module.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 生词巩固包实体
 */
@Data
@TableName("ai_review_content")
public class AiReviewContent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 巩固包标题 */
    private String title;
    /** 单词（逗号分隔，便于检索展示） */
    private String words;
    /** 巩固包完整 JSON */
    private String reviewJson;
    private LocalDateTime createdAt;
}
