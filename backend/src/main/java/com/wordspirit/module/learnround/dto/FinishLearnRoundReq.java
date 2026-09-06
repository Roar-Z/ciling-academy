package com.wordspirit.module.learnround.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 完成一轮学习后，持久化本轮（每个词 + 是否掌握）
 */
@Data
public class FinishLearnRoundReq {

    @NotBlank(message = "来源不能为空")
    private String source;

    @NotNull(message = "词数不能为空")
    private Integer count;

    @NotNull(message = "掌握数不能为空")
    private Integer masteredCount;

    /** 本轮学过的词（顺序即学习顺序） */
    private List<WordItem> words;

    @Data
    public static class WordItem {
        private String word;
        private Long wordId;
        private String phonetic;
        private String meaning;
        private String example;
        private String exampleCn;
        private Integer isMastered;
    }
}
