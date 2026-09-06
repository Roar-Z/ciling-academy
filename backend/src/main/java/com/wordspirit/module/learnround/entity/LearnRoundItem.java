package com.wordspirit.module.learnround.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习轮次 - 单词明细（冗余存储释义/音标/例句，避免词库变动后查不到）
 */
@Data
@TableName("learn_round_item")
public class LearnRoundItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roundId;
    private Long userId;
    private String word;
    /** 关联生词本ID（翻卡时手动加词后回填，可能为 null） */
    private Long wordId;
    private String phonetic;
    private String meaning;
    private String example;
    private String exampleCn;
    /** 用户是否标记为掌握 1=是 0=否/模糊/不记得 */
    private Integer isMastered;
    private LocalDateTime createdAt;
}
