package com.wordspirit.module.wordtest.dto;

import lombok.Data;

import java.util.List;

/**
 * 测验完成响应：含统计 + 错题回顾
 */
@Data
public class FinishTestResp {
    private Long batchId;
    private Integer total;
    private Integer correctCount;
    private Integer accuracy;       // 0-100
    private Integer costMsTotal;       // 总用时 ms
    /** 错题列表 */
    private List<WrongItem> wrongList;

    @Data
    public static class WrongItem {
        private Long wordId;
        private String word;
        private String phonetic;
        private String meaning;
        private String userAnswer;
        /** 下次复习时间点（艾宾浩斯精确到分钟） */
        private String nextReviewAt;
    }
}