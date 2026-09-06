package com.wordspirit.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("sys_user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** BCrypt 加密密码（不参与序列化返回） */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 邮箱（用于找回、通知） */
    private String email;

    /** 个人简介/座右铭 */
    private String bio;

    /** 今日已学单词数 */
    private Integer todayWords;

    /** 累计学习单词数 */
    private Integer totalWords;

    /** 累计掌握单词数（familiarity>=80 的不同单词数，去重，持久化不受生词本清理影响） */
    private Integer totalMastered;

    /** 已掌握单词集合 JSON 数组（去重，新词学习"认识"时直接加入，与生词本无关） */
    private String masteredWords;

    /** 连续学习天数 */
    private Integer studyDays;

    /** 每轮复习/学习单词数（偏好持久化，默认10） */
    private Integer reviewBatchSize;

    /** 每日学习目标（用户可自定义，默认20） */
    private Integer dailyGoal;

    /** 最后学习日期 */
    private LocalDate lastStudyDate;

    /** 今日AI调用次数（异步统计） */
    private Integer aiUsedToday;

    /** 累计AI调用次数（异步统计） */
    private Integer aiUsedTotal;

    /** 是否开启今日复习提醒通知（1=开 0=关，默认开） */
    private Integer notifyReview;

    /** 是否开启AI额度提醒通知（1=开 0=关，默认开） */
    private Integer notifyQuota;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
