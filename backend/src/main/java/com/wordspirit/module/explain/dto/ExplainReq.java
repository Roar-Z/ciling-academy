package com.wordspirit.module.explain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 阅读解析请求（模式A）
 */
@Data
public class ExplainReq {

    @NotBlank(message = "请输入要解析的英文文本")
    @Size(max = 3000, message = "文本过长，请控制在3000字符以内")
    private String text;
}
