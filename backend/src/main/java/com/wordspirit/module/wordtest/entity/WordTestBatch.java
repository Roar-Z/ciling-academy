package com.wordspirit.module.wordtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单词测验批次（一次测验的整体记录）
 *
 * mode      : spelling 看中文拼英文 / matching 中英连线 / sentence 翻译句子
 * source    : new 新词测验 / due 待复习测验
 */
@Data
@TableName("word_test_batch")
public class WordTestBatch implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 测验模式 */
    private String mode;
    /** 测验来源：new 新词 / due 待复习 */
    private String source;
    /** 本批词数 */
    private Integer total;
    /** 答对题数 */
    private Integer correctCount;
    /** 单题限时（秒） */
    private Integer timeLimitSec;
    /** 批次创建时间 */
    private LocalDateTime startedAt;
    /** 批次完成时间 */
    private LocalDateTime finishedAt;
}