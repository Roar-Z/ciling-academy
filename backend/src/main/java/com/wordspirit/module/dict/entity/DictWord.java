package com.wordspirit.module.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 单词词典实体（基础释义，MySQL+Redis，不消耗AI额度）
 */
@Data
@TableName("dict_word")
public class DictWord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String phonetic;
    private String pos;
    private String meaning;
    private String example;
    private String exampleCn;
    /** 难度1-5 */
    private Integer difficulty;
    /** 等级: cet4(四级)/cet6(六级)/other(其他) */
    private String level;
    /** 是否高频核心词 1=是 0=否 */
    private Integer isCore;
    /**
     * 关联的 word_book.id（不入库）。
     * 用于新词学习 / randomStudy 拉到"艾宾浩斯到期复习词"时，
     * 前端拿到 bookId 后调 reviewWord(bookId, familiar) 走 due 复习流程
     */
    private transient Long bookId;
}
