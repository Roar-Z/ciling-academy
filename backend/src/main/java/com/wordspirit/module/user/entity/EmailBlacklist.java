package com.wordspirit.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮箱黑名单（注销账号后冻结 7 天，期间不可用此邮箱注册）
 */
@Data
@TableName("sys_email_blacklist")
public class EmailBlacklist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 被冻结的邮箱（小写） */
    private String email;

    /** 注销时的用户ID（追溯用） */
    private Long userId;

    /** 冻结原因 */
    private String reason;

    /** 解冻时间 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt;
}