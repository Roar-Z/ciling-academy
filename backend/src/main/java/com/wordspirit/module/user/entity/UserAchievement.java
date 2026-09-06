package com.wordspirit.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户成就解锁记录（达成条件时由后端懒解锁写入，永久保留）
 */
@Data
@TableName("user_achievement")
public class UserAchievement implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 成就编码（与 AchievementDef.code 对应） */
    private String code;

    /** 达成时间 */
    private LocalDateTime unlockedAt;
}
