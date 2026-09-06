package com.wordspirit.module.notification;

/**
 * 通知类目（用于决定是否受用户偏好开关控制）
 *
 * <ul>
 *   <li>{@link #REVIEW}  —— 受「今日复习提醒」开关控制</li>
 *   <li>{@link #QUOTA}   —— 受「AI 额度提醒」开关控制</li>
 *   <li>{@link #ALWAYS}  —— 不受开关控制（业务强提示，如注册奖励、签到奖励）</li>
 * </ul>
 */
public enum NotifyCategory {
    REVIEW,
    QUOTA,
    ALWAYS
}