package com.wordspirit.module.wordtest.dto;

import lombok.Data;

import java.util.List;

/**
 * 开启测验的响应
 */
@Data
public class StartTestResp {
    private Long batchId;
    private String mode;
    private String source;
    private Integer total;
    private Integer timeLimitSec;
    /** 题目列表 */
    private List<TestQuestionDto> questions;
}