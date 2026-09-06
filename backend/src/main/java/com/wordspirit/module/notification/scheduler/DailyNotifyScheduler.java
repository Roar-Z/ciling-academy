package com.wordspirit.module.notification.scheduler;

import com.wordspirit.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日通知定时推送器
 *  - 每天北京时间 0:05（每日 AI 额度 0 点重置到账后）为所有用户自动推送：
 *    1) 「每日词灵AI已到账」通知（受 AI 额度提醒开关控制）
 *    2) 「今日待复习」通知（受今日复习提醒开关控制，仅当有待复习单词）
 *  - 内部按 Redis 日 key 防重：用户当天打开铃铛时若已推过，定时任务不会重复推送，反之亦然
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyNotifyScheduler {

    private final NotificationService notificationService;

    @Scheduled(cron = "0 5 0 * * ?", zone = "Asia/Shanghai")
    public void pushDailyNotify() {
        try {
            notificationService.pushDailyGiftForAll();
        } catch (Exception e) {
            // 定时任务失败不影响业务，用户进入 App 时前端仍会调用 daily-gift 接口兜底
            log.error("每日通知定时推送任务失败：{}", e.getMessage(), e);
        }
    }
}
