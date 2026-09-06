package com.wordspirit.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 每日任务模板：任务池，每日从中随机抽取推送，保证每日不重复
 */
@Data
@TableName("task_template")
public class TaskTemplate implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类别：review/newWord/aiUse/game/paper（对应 TASK_POINTS 的 key） */
    private String category;

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 图标名（前端 AppIcon 使用） */
    private String icon;

    /** 跳转路径 */
    private String path;

    /** 完成可得活跃度 */
    private Integer points;

    /** 抽取权重，越大越容易被抽中 */
    private Integer weight;

    /** 是否启用：1 启用 0 停用 */
    private Integer enabled;

    private LocalDateTime createdAt;
}
