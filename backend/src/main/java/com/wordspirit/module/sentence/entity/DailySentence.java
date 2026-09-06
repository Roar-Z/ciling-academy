package com.wordspirit.module.sentence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 句灵日选：每天 0:00 由服务端定时调用大模型生成 1 句英语佳句入库存档，
 * 前端任务页"句灵日选"模块读取展示；当日若生成失败则从历史随机抽取兜底。
 */
@Data
@TableName("daily_sentence")
public class DailySentence implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 推送日期（唯一索引 uk_date） */
    private LocalDate date;

    /** 英文原句 */
    private String enSentence;

    /** 中文释义 */
    private String cnTrans;

    /** 高分词块（如 "quiet persistence 默默坚持"） */
    private String keyCollocation;

    /** 主题标签（｜分隔，如 "成长｜坚持"） */
    private String tags;

    /** 意境/适用场景备注 */
    private String note;

    private LocalDateTime createdAt;
}