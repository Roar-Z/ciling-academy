package com.wordspirit.module.wordbook.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 加入生词本请求
 */
@Data
public class WordAddReq {

    @NotBlank(message = "单词不能为空")
    private String word;

    private String phonetic;
    private String meaning;
    /** 阅读解析要点JSON: grammar/colloc/synonym/explain/origin */
    private String usage;
    /** 来源 */
    private String source = "manual";
    /** 初始熟悉度 0~100。新词学习"认识"=80（一次即视为掌握）；其他来源默认 0 */
    private Integer initialFamiliarity;
}
