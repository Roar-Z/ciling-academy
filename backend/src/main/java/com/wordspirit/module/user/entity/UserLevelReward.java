package com.wordspirit.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户等级升级奖励领取记录（每级仅可领一次）
 */
@Data
@TableName("user_level_reward")
public class UserLevelReward implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 达成的等级（2-20） */
    private Integer level;

    /** 奖励金币数 */
    private Integer coinAward;

    /** 奖励永久AI次数 */
    private Integer aiAward;

    private LocalDateTime claimedAt;
}
