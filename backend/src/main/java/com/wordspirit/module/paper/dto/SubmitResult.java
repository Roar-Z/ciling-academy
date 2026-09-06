package com.wordspirit.module.paper.dto;

import lombok.Data;

import java.util.List;

/**
 * 交卷批改结果
 */
@Data
public class SubmitResult {

    private Long paperId;
    private String paperName;
    private Integer score;
    private Integer totalScore;
    private Integer correctCount;
    private Integer totalCount;
    /** 正确率（0-100 保留两位） */
    private Double correctRate;
    private List<QuestionResult> details;
}
