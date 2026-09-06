/*
 * @Author: Roar-Z 2175994007@qq.com
 * @Date: 2026-09-01 13:36:20
 * @LastEditors: Roar-Z 2175994007@qq.com
 * @LastEditTime: 2026-09-06 05:36:23
 * @FilePath: \wordSpirit\backend\src\main\java\com\wordspirit\module\dict\service\DictWordService.java
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
 */
package com.wordspirit.module.dict.service;

import com.wordspirit.module.dict.entity.DictWord;

import java.util.List;
import java.util.Map;

/**
 * 词典服务
 */
public interface DictWordService {

    /** 首页平台统计（游客可用）：wordCount 收录单词数 */
    Map<String, Long> platformStats();

    /** 查询单词释义（Redis 热点缓存 + MySQL） */
    DictWord lookup(String word);

    /** 批量查询释义 */
    List<DictWord> lookupBatch(List<String> words);

    /** 关键词搜索（分页） */
    Object search(String keyword, long page, long size);

    /** 随机取 N 个单词（词典兜底） */
    List<DictWord> randomFromDict(int count);

    /**
     * 随机取 N 个单词，支持按 level 过滤
     * level: cet4 / cet6 / mixed / all(或不传)
     * - cet4: 仅 CET4（difficulty=2）
     * - cet6: 仅 CET6（difficulty=4）
     * - mixed: CET4 + CET6（difficulty IN 2,4）
     * - all/不传: 不限
     */
    List<DictWord> randomFromDict(int count, String level);

    /**
     * 随机取 N 个单词，支持 level 过滤 + 是否仅高频核心词
     * @param coreOnly 为 true 时只取 is_core=1 的高频核心词；为 null/false 时不限制
     */
    List<DictWord> randomFromDict(int count, String level, Boolean coreOnly);

    /**
     * 随机取 N 个单词，排除用户已学过的（生词本里的单词）
     * 用于"学习新词 —— 再来一轮换新词"，避免出现已学过的词
     * @param userId 当前用户；为 null 时不排除
     */
    List<DictWord> randomUnlearned(int count, String level, Long userId);
}
