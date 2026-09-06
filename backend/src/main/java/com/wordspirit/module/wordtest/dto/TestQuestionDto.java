package com.wordspirit.module.wordtest.dto;

import lombok.Data;

import java.util.List;

/**
 * 单题给前端的结构
 */
@Data
public class TestQuestionDto {
    private Long wordId;
    private String word;
    private String phonetic;
    private String meaning;
    private String pos;
    private String example;
    private String exampleCn;

    /** matching 模式专用：英文候选词（含正确答案 + 干扰项） */
    private List<String> choicesEn;
    /** matching 模式专用：中文候选义（含正确答案 + 干扰项） */
    private List<String> choicesCn;
}