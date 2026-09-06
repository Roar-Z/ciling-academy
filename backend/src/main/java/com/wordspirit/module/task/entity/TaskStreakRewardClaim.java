package com.wordspirit.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 连续打卡奖励领取记录
 * 同一用户同一 streak_days 仅可领一次（由 uk_user_streak 保证）
 */
@Data
@TableName("task_streak_reward_claim")
public class TaskStreakRewardClaim implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 连续天数里程碑：7 / 14 / 21 / 30 */
    private Integer streakDays;

    /** 奖励的 AI 每日额度 */
    private Integer aiQuotaAward;

    /** 奖励的金币 */
    private Integer coinAward;

    private LocalDateTime claimedAt;
}