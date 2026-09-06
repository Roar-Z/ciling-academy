package com.wordspirit.module.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 保存游戏记录请求
 */
@Data
public class GameRecordReq {

    @NotBlank(message = "游戏标识不能为空")
    private String gameId;

    private String gameName;

    @NotNull(message = "得分不能为空")
    private Integer score;

    private Integer correctCount = 0;

    private Integer totalCount = 0;

    private Integer durationSec = 0;
}
