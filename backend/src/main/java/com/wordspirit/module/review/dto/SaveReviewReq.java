package com.wordspirit.module.review.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 保存生词巩固包请求（用户主动点击"保存这份助记内容"时提交原始JSON）
 */
@Data
public class SaveReviewReq {

    /** AI 生成的原始 JSON（后端严格校验） */
    @NotBlank(message = "JSON数据不能为空")
    private String jsonData;

    /** 来源消息ID（可选，用于回跳） */
    private Long messageId;
}
