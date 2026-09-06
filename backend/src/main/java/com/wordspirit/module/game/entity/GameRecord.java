package com.wordspirit.module.game.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 游戏记录实体
 */
@Data
@TableName("game_record")
public class GameRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 游戏标识 game1~game10 */
    private String gameId;
    private String gameName;
    private Integer score;
    private Integer coins;
    private Integer correctCount;
    private Integer totalCount;
    private BigDecimal correctRate;
    private Integer durationSec;
    private LocalDateTime createdAt;
}
