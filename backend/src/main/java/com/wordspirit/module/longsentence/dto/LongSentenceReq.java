package com.wordspirit.module.longsentence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 长难句分析请求
 */
@Data
public class LongSentenceReq {

    /** 待分析的英文长难句 */
    @NotBlank(message = "句子不能为空")
    @Size(max = 2000, message = "句子长度不能超过 2000 字符")
    private String sentence;
}