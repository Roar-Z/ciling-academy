package com.wordspirit.module.learnround.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 某一轮学习详情（回看/出题用）
 */
@Data
public class LearnRoundDetailDto {
    private Long roundId;
    private Integer roundNo;
    private String source;
    private Integer count;
    private Integer masteredCount;
    private LocalDateTime finishedAt;
    private List<LearnRoundWordDto> words;

    @Data
    public static class LearnRoundWordDto {
        private Long wordId;
        private String word;
        private String phonetic;
        private String meaning;
        private Integer isMastered;
    }
}
