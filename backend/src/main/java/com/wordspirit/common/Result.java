package com.wordspirit.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应体
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private long timestamp = System.currentTimeMillis();

    public static <T> Result<T> ok() {
        return build(ResultCode.SUCCESS, null);
    }

    public static <T> Result<T> ok(T data) {
        return build(ResultCode.SUCCESS, data);
    }

    public static <T> Result<T> fail(ResultCode code) {
        return build(code, null);
    }

    public static <T> Result<T> fail(ResultCode code, String message) {
        Result<T> r = build(code, null);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> r = build(ResultCode.SERVER_ERROR, null);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> build(ResultCode code, T data) {
        Result<T> r = new Result<>();
        r.setCode(code.getCode());
        r.setMessage(code.getMessage());
        r.setData(data);
        return r;
    }

    public boolean isSuccess() {
        return code == ResultCode.SUCCESS.getCode();
    }
}
