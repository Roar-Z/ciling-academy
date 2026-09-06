package com.wordspirit.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务活跃度奖励领取记录
 * 活跃度每日重置，领取记录也按天隔离：同一用户同一天同一 threshold 仅可领一次
 * （由 uk_user_threshold_date 保证）
 */
@Data
@TableName("task_active_reward_claim")
public class TaskActiveRewardClaim implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 活跃度阈值：25 / 50 / 75 / 100 */
    private Integer threshold;

    /** 领取日期（活跃度按天重置，跨天可再次领取同档位） */
    private LocalDate claimDate;

    /** 奖励的金币 */
    private Integer coinAward;

    /** 奖励的 AI 每日额度 */
    private Integer aiQuotaAward;

    private LocalDateTime claimedAt;
}