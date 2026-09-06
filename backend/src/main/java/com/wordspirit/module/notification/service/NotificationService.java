package com.wordspirit.module.notification.service;

import com.wordspirit.module.notification.NotifyCategory;
import com.wordspirit.module.notification.entity.SysNotification;

import java.util.List;

/**
 * 系统通知服务
 *
 * <p>职责：
 * <ul>
 *   <li>推送通知（业务侧埋点调用）</li>
 *   <li>每用户最多保留 30 条，超出自动删除最早的</li>
 *   <li>前端查询 / 标记已读 / 全部已读</li>
 *   <li>每日赠送 AI 调用 + 今日复习提醒（按 Redis 日 key 防重，受用户偏好控制）</li>
 * </ul>
 */
public interface NotificationService {

    /** 无条件推送一条通知（自动清理到 30 条上限） */
    void push(Long userId, String type, String title, String content);

    /**
     * 按类目门控的推送：会根据用户的偏好设置决定是否真的写入通知
     *
     * @param category 参考 {@link NotifyCategory}：REVIEW 受复习开关控制，QUOTA 受AI额度开关控制，ALWAYS 不受控制
     */
    void pushIfEnabled(Long userId, NotifyCategory category, String type, String title, String content);

    /** 最近 30 条（按时间倒序） */
    List<SysNotification> listRecent(Long userId);

    /** 未读数量（用于铃铛红点） */
    long unreadCount(Long userId);

    /** 标记单条已读 */
    void markRead(Long userId, Long id);

    /** 全部标记已读 */
    void markAllRead(Long userId);

    /**
     * 每日首次进入 App 时调用一次：根据用户偏好可能推送
     * <ul>
     *   <li>每日词灵AI 赠送通知（受 AI 额度提醒开关控制）</li>
     *   <li>今日复习提醒通知（受今日复习提醒开关控制，仅当有待复习单词时推送）</li>
     * </ul>
     * 通过 Redis 日 key 防重，避免每日重复打扰。
     *
     * @return 今日是否首次进入（true=本次是今日首次调用，false=今日已调用过）
     */
    boolean pushDailyGiftIfNeeded(Long userId);

    /**
     * 定时任务入口：为所有用户执行每日赠送/复习提醒推送。
     * 内部逐用户调用 {@link #pushDailyGiftIfNeeded}（Redis 日 key 防重 + 偏好开关门控），
     * 单用户失败不影响其他用户。
     */
    void pushDailyGiftForAll();
}