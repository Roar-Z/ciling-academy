package com.wordspirit.module.user.service;

import java.util.Map;

/**
 * 用户成就服务：条件定义 + 懒解锁 + 列表查询
 */
public interface AchievementService {

    /**
     * 成就列表（查询时自动检测并解锁已达成的成就，记录达成时间）
     * 返回：list（全量成就+状态）、unlockedCount、totalPoints、unlockedPoints
     */
    Map<String, Object> achievements(Long userId);
}
