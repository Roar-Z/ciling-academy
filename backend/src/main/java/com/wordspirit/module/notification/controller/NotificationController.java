package com.wordspirit.module.notification.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.notification.entity.SysNotification;
import com.wordspirit.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统通知接口
 *  - GET    /api/notification/list              最近 30 条
 *  - GET    /api/notification/unread-count      未读数量（铃铛红点）
 *  - POST   /api/notification/read/{id}         标记单条已读
 *  - POST   /api/notification/read-all          全部标记已读
 *  - POST   /api/notification/daily-gift       App 进入时每日一次 AI 赠送通知
 */
@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/list")
    public Result<List<SysNotification>> list() {
        return Result.ok(notificationService.listRecent(UserContext.requireUserId()));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.ok(Map.of("count", notificationService.unreadCount(UserContext.requireUserId())));
    }

    @PostMapping("/read/{id}")
    public Result<Void> read(@PathVariable Long id) {
        notificationService.markRead(UserContext.requireUserId(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> readAll() {
        notificationService.markAllRead(UserContext.requireUserId());
        return Result.ok();
    }

    @PostMapping("/daily-gift")
    public Result<Map<String, Object>> dailyGift() {
        boolean pushed = notificationService.pushDailyGiftIfNeeded(UserContext.requireUserId());
        return Result.ok(Map.of("pushed", pushed));
    }
}