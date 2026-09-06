package com.wordspirit.module.paper.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 试卷主表实体
 */
@Data
@TableName("exercise_paper")
public class ExercisePaper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String paperName;
    private String paperIntro;
    /** 考点小结 */
    private String pointSummary;
    /** 来源: ai/manual */
    private String source;
    /** 内容指纹（防同一份试卷重复导入） */
    private String contentHash;
    private Integer questionCount;
    private Integer totalScore;
    private Integer bestScore;
    private Integer doneCount;
    private String status;
    private LocalDateTime createdAt;
}
