package com.wordspirit.module.translate.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 翻译请求
 * <p>mode=normal 走第三方翻译 API；mode=ai 走词灵 AI（消耗 1 次额度）</p>
 */
@Data
public class TranslateReq {

    @NotBlank(message = "原文不能为空")
    private String text;

    /** normal | ai */
    @NotBlank(message = "翻译模式不能为空")
    private String mode = "normal";

    /** zh | en | auto（默认 auto 自动检测） */
    private String srcLang = "auto";

    /** zh | en（默认按 srcLang 自动取反） */
    private String tgtLang = "auto";
}