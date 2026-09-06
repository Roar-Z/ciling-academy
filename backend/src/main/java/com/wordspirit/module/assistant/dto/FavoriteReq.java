package com.wordspirit.module.assistant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收藏 AI 消息请求
 */
@Data
public class FavoriteReq {

    @NotNull(message = "消息ID不能为空")
    private Long messageId;
}
