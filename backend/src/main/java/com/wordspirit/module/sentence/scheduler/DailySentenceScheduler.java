package com.wordspirit.module.sentence.scheduler;

import com.wordspirit.module.sentence.service.DailySentenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 句灵日选定时生成器
 *  - 每天北京时间 0:00 调大模型生成当日日选并入库
 *  - 调用走 AiService.rawChat，不占用用户 AI 额度
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailySentenceScheduler {

    private final DailySentenceService dailySentenceService;

    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void generateTodaySentence() {
        try {
            dailySentenceService.generateToday();
        } catch (Exception e) {
            // 生成失败不抛（避免影响后续定时任务），前端会从历史兜底
            log.error("每日句灵日选生成失败：{}", e.getMessage(), e);
        }
    }
}