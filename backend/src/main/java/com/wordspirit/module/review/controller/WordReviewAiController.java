package com.wordspirit.module.review.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.review.dto.SaveReviewReq;
import com.wordspirit.module.review.entity.AiReviewContent;
import com.wordspirit.module.review.service.WordReviewAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 生词巩固接口（模式B 导入）
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class WordReviewAiController {

    private final WordReviewAiService reviewService;

    /** 保存巩固包（用户主动点击导入） */
    @PostMapping("/save")
    public Result<AiReviewContent> save(@Valid @RequestBody SaveReviewReq req) {
        return Result.ok(reviewService.save(UserContext.requireUserId(), req));
    }

    /** 巩固包列表 */
    @GetMapping("/list")
    public Result<List<AiReviewContent>> list() {
        return Result.ok(reviewService.list(UserContext.requireUserId()));
    }

    /** 巩固包详情 */
    @GetMapping("/{id}")
    public Result<AiReviewContent> detail(@PathVariable Long id) {
        return Result.ok(reviewService.detail(UserContext.requireUserId(), id));
    }

    /** 删除巩固包 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        reviewService.remove(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
