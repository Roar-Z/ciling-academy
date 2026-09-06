package com.wordspirit.module.sentence.controller;

import com.wordspirit.common.Result;
import com.wordspirit.module.sentence.dto.DailySentenceVO;
import com.wordspirit.module.sentence.service.DailySentenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 句灵日选接口
 *  - GET /api/sentence/today  今日日选（按 Redis 缓存 → DB → 历史兜底 顺序）
 */
@RestController
@RequestMapping("/api/sentence")
@RequiredArgsConstructor
public class DailySentenceController {

    private final DailySentenceService dailySentenceService;

    @GetMapping("/today")
    public Result<DailySentenceVO> today() {
        return Result.ok(dailySentenceService.getToday());
    }
}