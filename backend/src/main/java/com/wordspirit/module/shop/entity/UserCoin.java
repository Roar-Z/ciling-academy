package com.wordspirit.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户金币实体
 */
@Data
@TableName("user_coin")
public class UserCoin implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer coinBalance;
    private Integer totalEarned;
    private Integer totalSpent;
}
