package com.wordspirit.module.wordtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开启一次测验
 */
@Data
public class StartTestReq {

    /** 模式：spelling 看中文拼写英文 / matching 中英连线 / sentence 翻译句子 */
    @NotBlank(message = "模式不能为空")
    private String mode;

    /** 来源：new 新词测验 / due 待复习测验 */
    @NotBlank(message = "来源不能为空")
    private String source;

    /** 词数 */
    @NotNull(message = "词数不能为空")
    private Integer count;

    /** 单题限时（秒），可选，默认按模式给 */
    private Integer timeLimitSec;

    /**
     * 重做某轮：指定后，从这一轮（batchId）的题目复用词表开启新一轮测验，
     * 用于"巩固测验"——针对之前做过的词再测一遍。为空则按 source 随机抽词。
     */
    private Long reuseBatchId;

    /**
     * 巩固测验：指定后，从某一"学习轮次"（learn_round）的词表开启测验，
     * 用于"选择哪一轮新词做测验 / 重测"。优先于 reuseBatchId、source。
     */
    private Long learnRoundId;
}