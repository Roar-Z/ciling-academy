package com.wordspirit.module.dict.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.dict.entity.DictWord;
import com.wordspirit.module.dict.service.DictWordService;
import com.wordspirit.module.wordbook.service.StudyCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 词典接口（游客可访问：登录拦截器已放行 /api/dict/**）
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictWordController {

    private final DictWordService dictWordService;
    private final StudyCacheService studyCacheService;

    /** 查询单词释义（基础功能，不消耗AI额度） */
    @GetMapping("/word/{word}")
    public Result<DictWord> lookup(@PathVariable String word) {
        return Result.ok(dictWordService.lookup(word));
    }

    /** 词典搜索（分页） */
    @GetMapping("/search")
    public Result<Object> search(String keyword, Long page, Long size) {
        return Result.ok(dictWordService.search(keyword,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 首页平台统计（游客可用）：收录单词数 + 全站累计掌握单词数 */
    @GetMapping("/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(dictWordService.platformStats());
    }

    /**
     * 随机取 N 个单词（新词学习）
     * - 可选 level=cet4|cet6|mixed|all
     * - 登录用户：走优先级推送（生词本待复习 > 高频核心 > 普通词）
     * - 游客：纯词典随机 + level 过滤
     * - excludeLearned=true（仅登录用户）：排除用户已学过的词，且每次真随机，
     *   用于"学习新词 —— 再来一轮换新词"，避免重复出现已学词汇
     */
    @GetMapping("/random")
    public Result<List<DictWord>> random(@RequestParam(required = false) Integer count,
                                         @RequestParam(required = false) String level,
                                         @RequestParam(required = false, defaultValue = "false") Boolean excludeLearned) {
        int n = count == null ? 10 : count;
        Long userId = UserContext.getUserId();
        if (Boolean.TRUE.equals(excludeLearned) && userId != null) {
            return Result.ok(dictWordService.randomUnlearned(n, level, userId));
        }
        return Result.ok(studyCacheService.take(userId, level, n));
    }
}
