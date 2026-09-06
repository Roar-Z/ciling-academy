package com.wordspirit.module.paper.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单题作答
 */
@Data
public class AnswerItem {

    @NotNull(message = "题目ID不能为空")
    private Long questionId;

    /** 用户答案（match 为 JSON 数组字符串，如 "[0,2,1,3]"） */
    private String answer;
}
