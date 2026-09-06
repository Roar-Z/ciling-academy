package com.wordspirit.module.sentence.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wordspirit.common.PageResult;
import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.sentence.dto.SentenceAddReq;
import com.wordspirit.module.sentence.entity.Sentence;
import com.wordspirit.module.sentence.service.SentenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 句灵集接口
 */
@RestController
@RequestMapping("/api/sentence")
@RequiredArgsConstructor
public class SentenceController {

    private final SentenceService sentenceService;

    /** 分页查询我的句灵集 */
    @GetMapping("/list")
    public Result<Object> list(String keyword, Long page, Long size) {
        Page<Sentence> p = sentenceService.list(UserContext.requireUserId(), keyword,
                page == null ? 1 : page, size == null ? 20 : size);
        return Result.ok(PageResult.of(p));
    }

    /** 当前用户已收藏的原文集合（用于前端按钮"已收藏"状态） */
    @GetMapping("/collected-originals")
    public Result<java.util.Set<String>> collectedOriginals() {
        return Result.ok(sentenceService.listCollectedOriginals(UserContext.requireUserId()));
    }

    /** 加入句灵集 */
    @PostMapping("/add")
    public Result<Sentence> add(@Valid @RequestBody SentenceAddReq req) {
        return Result.ok(sentenceService.add(UserContext.requireUserId(), req));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        sentenceService.remove(UserContext.requireUserId(), id);
        return Result.ok();
    }

    /** 批量删除 */
    @PostMapping("/batch-remove")
    public Result<Void> batchRemove(@RequestBody List<Long> ids) {
        if (ids != null) {
            for (Long id : ids) {
                sentenceService.remove(UserContext.requireUserId(), id);
            }
        }
        return Result.ok();
    }
}