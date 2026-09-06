package com.wordspirit.module.task.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.task.entity.TaskActiveRewardClaim;
import com.wordspirit.module.task.entity.TaskStreakRewardClaim;
import com.wordspirit.module.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务中心接口
 *  - GET  /api/task/today               今日面板（任务、打卡状态、连续天数、活跃度）
 *  - POST /api/task/check-in            每日打卡（幂等）
 *  - POST /api/task/streak-reward/claim 领取连续奖励（参数 streakDays=7/14/21/30）
 *  - POST /api/task/active-reward/claim 领取活跃度奖励（参数 threshold=30/60/100/200）
 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/today")
    public Result<Map<String, Object>> today() {
        return Result.ok(taskService.todayPanel(UserContext.requireUserId()));
    }

    @PostMapping("/check-in")
    public Result<Map<String, Object>> checkIn() {
        return Result.ok(taskService.doCheckIn(UserContext.requireUserId()));
    }

    @PostMapping("/streak-reward/claim")
    public Result<Map<String, Object>> claimStreak(@RequestParam Integer streakDays) {
        return Result.ok(taskService.claimStreakReward(UserContext.requireUserId(), streakDays));
    }

    @PostMapping("/active-reward/claim")
    public Result<Map<String, Object>> claimActive(@RequestParam Integer threshold) {
        return Result.ok(taskService.claimActiveReward(UserContext.requireUserId(), threshold));
    }

    /** 给前端用的查询接口（已领取列表，备用） */
    @GetMapping("/streak-rewards")
    public Result<List<TaskStreakRewardClaim>> streakRewards() {
        return Result.ok(taskService.streakClaims(UserContext.requireUserId()));
    }

    @GetMapping("/active-rewards")
    public Result<List<TaskActiveRewardClaim>> activeRewards() {
        return Result.ok(taskService.activeClaims(UserContext.requireUserId()));
    }
}