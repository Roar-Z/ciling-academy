package com.wordspirit.module.wordtest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 提交一道题的回答
 */
@Data
public class SubmitAnswerReq {

    @NotNull(message = "批次ID不能为空")
    private Long batchId;

    @NotNull(message = "单词ID不能为空")
    private Long wordId;

    /** 0 答错 / 1 答对 */
    @NotNull(message = "结果不能为空")
    private Integer correct;

    /** 用时毫秒 */
    private Integer costMs;

    /** 用户作答（spelling 存输入；matching/sentence 存选中项 id 或翻译文本） */
    private String userAnswer;
}