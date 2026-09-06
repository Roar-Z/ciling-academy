package com.wordspirit.module.sentence.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;

/**
 * 句灵日选 VO：前端任务页"句灵日选"卡片展示
 */
@Data
public class DailySentenceVO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 英文原句 */
    private String enSentence;

    /** 中文释义 */
    private String cnTrans;

    /** 高分词块（英文+中文 同行） */
    private String keyCollocation;

    /** 主题标签（｜分隔） */
    private String tags;

    /** 意境/适用场景备注 */
    private String note;

    /**
     * 是否历史兜底（true 表示今天 0:00 定时任务未成功生成，前端展示一句历史佳句）
     */
    private Boolean fallback;
}