package com.wordspirit.common;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Long requireUserId() {
        Long uid = USER_ID.get();
        if (uid == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return uid;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
