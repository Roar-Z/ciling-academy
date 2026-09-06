package com.wordspirit.common;

import lombok.Getter;

/**
 * 统一响应码
 */
@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "登录状态已失效，请重新登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    SERVER_ERROR(500, "服务器开小差了，请稍后再试"),

    // ---- AI 专用错误码（前端 axios 拦截器据此做友好提示）----
    AI_UNAVAILABLE(600, "词灵AI暂时不可用，请稍后再试，其他学习功能不受影响"),
    AI_QUOTA_EXCEEDED(601, "今日词灵AI额度已用完，明天再来试试吧"),
    AI_RATE_LIMITED(602, "操作太频繁啦，请稍后再试"),
    AI_FORMAT_ERROR(603, "词灵AI返回内容格式异常，请重新生成"),
    PAPER_DUPLICATE(604, "这份试卷已经导入过了，无需重复导入");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
