package com.wordspirit.ai;

import com.wordspirit.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 全局异常处理器：
 * 将额度不足、限流、服务不可用、格式异常统一转为友好提示，
 * 绝不向前端暴露堆栈与内部细节。
 */
@Slf4j
@RestControllerAdvice
public class GlobalAiExceptionHandler {

    @ExceptionHandler(AiException.class)
    public Result<Void> handleAiException(AiException e) {
        log.warn("词灵AI异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }
}
