package com.wordspirit.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日打卡记录
 */
@Data
@TableName("task_check_in")
public class TaskCheckIn implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 打卡日期 */
    private LocalDate checkInDate;

    /** 本次打卡后的连续天数（1 / 2 / 3 ...） */
    private Integer streakAfter;

    private LocalDateTime createdAt;
}