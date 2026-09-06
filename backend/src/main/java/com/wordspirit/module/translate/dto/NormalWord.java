package com.wordspirit.module.translate.dto;

import lombok.Data;

/**
 * 普通翻译逐词解析项（基于本地词库）
 */
@Data
public class NormalWord {

    /** 单词（小写） */
    private String word;

    /** 音标 */
    private String phonetic;

    /** 词性 */
    private String pos;

    /** 释义 */
    private String meaning;

    /** 词库是否收录 */
    private boolean found;
}