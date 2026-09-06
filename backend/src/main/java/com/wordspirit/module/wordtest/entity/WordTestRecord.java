package com.wordspirit.module.wordtest.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 单词测验答题记录（每题一行）
 */
@Data
@TableName("word_test_record")
public class WordTestRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long batchId;
    private Long wordId;
    private String word;
    private String mode;
    /** 0 答错，1 答对 */
    private Integer correct;
    /** 用时毫秒 */
    private Integer costMs;
    /** 用户作答（spelling 模式存用户输入；其他模式存选中项） */
    private String userAnswer;
    private LocalDateTime testedAt;
}