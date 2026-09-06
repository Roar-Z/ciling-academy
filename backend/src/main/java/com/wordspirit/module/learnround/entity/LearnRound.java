package com.wordspirit.module.learnround.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习轮次：每次"学习新词/复习"产生的批次快照（含本轮学的所有词）
 *
 * source: new 新词 / due 待复习
 */
@Data
@TableName("learn_round")
public class LearnRound implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 来源：new 新词 / due 待复习 */
    private String source;
    /** 本轮学过的词数 */
    private Integer count;
    /** 标记掌握的词数 */
    private Integer masteredCount;
    /** 开始时间 */
    private LocalDateTime startedAt;
    /** 完成时间 */
    private LocalDateTime finishedAt;
}
