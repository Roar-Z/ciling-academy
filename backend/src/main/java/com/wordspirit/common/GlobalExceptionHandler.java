package com.wordspirit.common;

import cn.hutool.core.exceptions.ValidateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：将异常统一转换为 Result，绝不把堆栈暴露给前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常（AI 相关由 GlobalAiExceptionHandler 单独接管） */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        ResultCode rc = switch (e.getCode()) {
            case 401 -> ResultCode.UNAUTHORIZED;
            case 403 -> ResultCode.FORBIDDEN;
            case 404 -> ResultCode.NOT_FOUND;
            case 604 -> ResultCode.PAPER_DUPLICATE;
            default -> ResultCode.BAD_REQUEST;
        };
        return Result.fail(rc, e.getMessage());
    }

    /** 参数校验异常 @Valid */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(BindException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + " " + fe.getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    /** 参数格式异常 */
    @ExceptionHandler({IllegalArgumentException.class, ValidateException.class})
    public Result<Void> handleIllegalArg(Exception e) {
        return Result.fail(ResultCode.BAD_REQUEST, e.getMessage());
    }

    /** 兜底异常 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}
