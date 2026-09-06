package com.wordspirit.module.task.service;

import com.wordspirit.module.task.entity.TaskActiveRewardClaim;
import com.wordspirit.module.task.entity.TaskCheckIn;
import com.wordspirit.module.task.entity.TaskStreakRewardClaim;

import java.util.List;
import java.util.Map;

/**
 * 任务中心服务：每日打卡、连续奖励、活跃度奖励、今日任务清单
 */
public interface TaskService {

    /** 一次性聚合：今日任务面板全部数据 */
    Map<String, Object> todayPanel(Long userId);

    /** 每日打卡（幂等：同日重复调用直接返回当前状态） */
    Map<String, Object> doCheckIn(Long userId);

    /** 领取连续打卡奖励（7 / 14 / 21 / 30，幂等） */
    Map<String, Object> claimStreakReward(Long userId, int streakDays);

    /** 领取今日活跃度奖励（30 / 60 / 100 / 200，幂等） */
    Map<String, Object> claimActiveReward(Long userId, int threshold);

    /** 该用户已领取的连续奖励（用于 UI 标灰） */
    List<TaskStreakRewardClaim> streakClaims(Long userId);

    /** 该用户已领取的活跃度奖励 */
    List<TaskActiveRewardClaim> activeClaims(Long userId);

    /** 当前连续天数（不含今天；若今天已打卡则含今天） */
    int currentStreak(Long userId);

    /** 今天是否已打卡 */
    boolean isCheckedInToday(Long userId);

    /** 今日活跃度得分（前端进度条展示） */
    int todayActivityPoints(Long userId);

    /** 每日 0:00 定时刷新：为所有用户生成当天随机任务（幂等） */
    void rotateDailyTasks();
}