package com.wordspirit.module.longsentence.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.longsentence.dto.LongSentenceReq;
import com.wordspirit.module.longsentence.dto.LongSentenceResp;
import com.wordspirit.module.longsentence.service.LongSentenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 长难句分析接口
 *
 * <p>模式与阅读助手一致：单次调用、即时返回、不进入对话历史。</p>
 */
@RestController
@RequestMapping("/api/ai/long-sentence")
@RequiredArgsConstructor
public class LongSentenceController {

    private final LongSentenceService longSentenceService;

    @PostMapping("/analyze")
    public Result<LongSentenceResp> analyze(@Valid @RequestBody LongSentenceReq req) {
        return Result.ok(longSentenceService.analyze(UserContext.requireUserId(), req));
    }
}