package com.wordspirit.module.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统通知
 *
 * <p>类型 type 取值：
 * <ul>
 *   <li>{@code ai_daily} — 每日 AI 赠送</li>
 *   <li>{@code register_bonus} — 注册赠送</li>
 *   <li>{@code reward_check_in} — 每日签到奖励</li>
 *   <li>{@code reward_streak} — 连续打卡里程碑奖励</li>
 *   <li>{@code reward_active} — 今日活跃度档位奖励</li>
 *   <li>{@code gen} — 内容生成完成</li>
 *   <li>{@code system} — 系统通知</li>
 * </ul>
 *
 * <p>每个用户最多保留 30 条，由 service 在插入新记录后自动清理超出。
 */
@Data
@TableName("sys_notification")
public class SysNotification implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户 ID */
    private Long userId;

    /** 通知类型 */
    private String type;

    /** 标题（短） */
    private String title;

    /** 详情（一行正文） */
    private String content;

    /** 是否已读 0=未读 1=已读 */
    private Integer isRead;

    private LocalDateTime createdAt;
}