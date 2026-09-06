package com.wordspirit.module.wordbook.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 生词本实体
 */
@Data
@TableName("word_book")
public class WordBook implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String word;
    private String phonetic;
    private String meaning;
    /** 来源: manual手动/reading阅读/ai AI助手/new_forget新词学习-不记得/new_vague新词学习-模糊/test巩固测验答错 */
    private String source;
    /** 熟悉度 0-100 */
    private Integer familiarity;
    /** 复习次数 */
    private Integer reviewCount;
    /** 艾宾浩斯下次复习时间点（精确到分钟） */
    private LocalDateTime nextReviewAt;
    private LocalDateTime lastReviewAt;
    /** 首次掌握时间（熟悉度首次>=80 时打标，防重复计数；NULL=尚未掌握） */
    private LocalDateTime masteredAt;
    /** AI助记内容JSON */
    private String aiNote;
    /** 阅读解析要点JSON: grammar/colloc/synonym/explain/origin（列名 word_usage，避开 MySQL 关键字） */
    private String wordUsage;
    private LocalDateTime createdAt;
}
