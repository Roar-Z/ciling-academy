package com.wordspirit.module.paper.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 交卷请求
 */
@Data
public class SubmitReq {

    @NotNull(message = "试卷ID不能为空")
    private Long paperId;

    @NotEmpty(message = "请完成作答后再交卷")
    private List<AnswerItem> answers;
}
