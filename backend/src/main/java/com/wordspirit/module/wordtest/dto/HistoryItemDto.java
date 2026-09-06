package com.wordspirit.module.wordtest.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史轮次列表项
 *
 * roundNo：按 startedAt 升序给的"第几轮"序号（1-based），全局唯一且持久
 */
@Data
public class HistoryItemDto {
    private Long batchId;
    private Integer roundNo;
    private String mode;
    private String source;
    private Integer total;
    private Integer correctCount;
    /** 正确率 0-100 */
    private Integer accuracy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
