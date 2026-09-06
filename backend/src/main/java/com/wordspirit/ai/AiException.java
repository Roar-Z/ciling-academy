package com.wordspirit.ai;

import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;

/**
 * AI 专属异常：由 GlobalAiExceptionHandler 统一捕获，返回友好提示
 */
public class AiException extends BusinessException {

    public AiException(ResultCode resultCode) {
        super(resultCode);
    }

    public AiException(ResultCode resultCode, String message) {
        super(resultCode.getCode(), message);
    }

    public AiException(int code, String message) {
        super(code, message);
    }
}
