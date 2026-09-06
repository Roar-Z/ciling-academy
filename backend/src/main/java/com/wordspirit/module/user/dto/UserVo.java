package com.wordspirit.module.user.dto;

import lombok.Data;

/**
 * 用户视图对象（不含密码）
 */
@Data
public class UserVo {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    /** 邮箱 */
    private String email;
    /** 个人简介/座右铭 */
    private String bio;
    /** 注册时间 */
    private java.time.LocalDateTime createdAt;
    /** 今日已学单词数 */
    private Integer todayWords;
    /** 累计学习单词数（包含重复复习次数，仅作历史统计） */
    private Integer totalWords;
    /** 累计掌握单词数（familiarity>=80 的不同单词数，去重） */
    private Integer masteredWords;
    /** 连续学习天数 */
    private Integer studyDays;
    /** 每轮复习/学习单词数（偏好持久化，默认10） */
    private Integer reviewBatchSize;
    /** 每日学习目标（默认20） */
    private Integer dailyGoal;
    /** 今日剩余AI额度 */
    private Integer aiQuotaRemain;
    /** 累计AI调用次数（异步统计） */
    private Integer aiUsedTotal;
    /** 是否开启今日复习提醒通知 */
    private Boolean notifyReview;
    /** 是否开启AI额度提醒通知 */
    private Boolean notifyQuota;
    /** 登录令牌 */
    private String token;
}
