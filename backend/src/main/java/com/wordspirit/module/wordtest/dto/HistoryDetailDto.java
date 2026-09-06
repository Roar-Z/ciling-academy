package com.wordspirit.module.wordtest.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 某一轮测验的详情（回看用）
 *
 * items 是该轮逐题记录：单词 + 用户作答 + 对错，用于"巩固测验"页面回看上一轮。
 */
@Data
public class HistoryDetailDto {
    private Long batchId;
    private Integer roundNo;
    private String mode;
    private String source;
    private Integer total;
    private Integer correctCount;
    private Integer accuracy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<HistoryDetailItem> items;

    @Data
    public static class HistoryDetailItem {
        private Long wordId;
        private String word;
        private String phonetic;
        private String meaning;
        private String mode;
        /** 0 答错，1 答对 */
        private Integer correct;
        /** 用户作答 */
        private String userAnswer;
        /** 用时毫秒 */
        private Integer costMs;
    }
}
