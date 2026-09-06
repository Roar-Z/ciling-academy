package com.wordspirit.module.wordbook.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量加入生词本请求（阅读助手一键收藏）
 */
@Data
public class BatchAddReq {

    @NotEmpty(message = "单词列表不能为空")
    private List<WordAddReq> words;
}
