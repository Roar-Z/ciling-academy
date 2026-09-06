package com.wordspirit.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户已拥有装扮实体
 */
@Data
@TableName("user_goods")
public class UserGoods implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long itemId;
    /** 是否佩戴 1/0 */
    private Integer equipped;
}
