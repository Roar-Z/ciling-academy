package com.wordspirit.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户每日任务：记录某用户某天被随机推送的任务，保证刷新不重复
 */
@Data
@TableName("user_daily_task")
public class UserDailyTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 推送日期（用户本地日） */
    private LocalDate taskDate;

    /** 关联任务模板 id */
    private Long templateId;

    /** 任务类别 */
    private String category;

    /** 是否完成（展示用，实际完成态由行为判定） */
    private Integer done;

    private LocalDateTime createdAt;
}
