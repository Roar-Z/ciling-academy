package com.wordspirit.module.learnround.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 词库独立已学词记录：用户在某个词库（cet4/cet6/gaokao/zhongkao/mixed）学过的词。
 * <p>
 * "完全独立词书"模式的持久化支撑——轮次满 {@code MAX_ROUNDS_PER_USER} 会被清空，
 * 本表不受清理影响，作为各词库取新词时的去重依据；跨词库互不影响。
 * level='all' 为存量数据迁移（历史轮次/生词本/已掌握），对所有词库生效。
 */
@Data
@TableName("user_pool_word")
public class UserPoolWord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** 词库档位：cet4/cet6/gaokao/zhongkao/mixed；all=存量迁移（全局排除） */
    private String level;
    private String word;
    /** 学该词时是否点了「认识」1=是 0=否（仅认识的词参与 60 天加深重现） */
    private Integer mastered;
    /** 已做过「加深印象」重现的时间；NULL=尚未重现过 */
    private LocalDateTime boostedAt;
    private LocalDateTime createdAt;
}
