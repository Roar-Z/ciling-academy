package com.wordspirit.module.paper.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 试卷导入请求（用户主动点击"导入为本套练习试卷"时提交原始JSON）
 */
@Data
public class PaperImportReq {

    /** AI 生成的试卷 JSON */
    @NotBlank(message = "JSON数据不能为空")
    private String jsonData;

    /** 来源消息ID（可选） */
    private Long messageId;
}
