package com.wordspirit.module.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目明细实体
 */
@Data
@TableName("exercise_question")
public class ExerciseQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long userId;
    /** 题型: en2cn/cn2en/spell_fill/context_choice/match */
    @JsonProperty("qType")
    private String qType;
    /** 题序 */
    private Integer seq;
    private String stem;
    /** 选项 JSON 数组（match 为空） */
    private String opts;
    /** 标准答案 JSON（match 为数组） */
    private String ans;
    private String analysis;
    private Integer score;
    /** 用户作答 JSON */
    private String userAnswer;
    /** 是否答对 0/1 */
    private Integer isCorrect;
    /** 已从错题本移除 0/1（不删题目本身，保留试卷完整性） */
    private Integer wrongExcluded;
    private LocalDateTime doneTime;
}
