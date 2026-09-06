package com.wordspirit.module.learnround.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习轮次列表项（历史）
 * roundNo：按完成时间升序给的"第几轮"序号（1-based），全局唯一持久
 */
@Data
public class LearnRoundDto {
    private Long roundId;
    private Integer roundNo;
    private String source;
    private Integer count;
    private Integer masteredCount;
    private LocalDateTime finishedAt;
}
