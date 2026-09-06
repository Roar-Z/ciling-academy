package com.wordspirit.module.explain.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.explain.dto.ExplainReq;
import com.wordspirit.module.explain.dto.ExplainResp;
import com.wordspirit.module.explain.service.WordExplainAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 阅读助手接口（模式A：就地调用）
 */
@RestController
@RequestMapping("/api/ai/explain")
@RequiredArgsConstructor
public class WordExplainAiController {

    private final WordExplainAiService explainService;

    /** 解析英文文本 */
    @PostMapping
    public Result<ExplainResp> explain(@Valid @RequestBody ExplainReq req) {
        return Result.ok(explainService.explain(UserContext.requireUserId(), req));
    }
}
