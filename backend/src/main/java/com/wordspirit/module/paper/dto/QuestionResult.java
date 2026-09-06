package com.wordspirit.module.paper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 单题批改结果
 */
@Data
public class QuestionResult {

    private Long questionId;
    private Integer seq;
    @JsonProperty("qType")
    private String qType;
    private String stem;
    private String opts;
    /** 用户答案 */
    private String userAnswer;
    /** 正确答案 */
    private String correctAnswer;
    private Boolean correct;
    private String analysis;
}
