package com.wordspirit.module.wordtest.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.wordtest.dto.FinishTestResp;
import com.wordspirit.module.wordtest.dto.HistoryDetailDto;
import com.wordspirit.module.wordtest.dto.HistoryItemDto;
import com.wordspirit.module.wordtest.dto.StartTestReq;
import com.wordspirit.module.wordtest.dto.StartTestResp;
import com.wordspirit.module.wordtest.service.WordTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/**
 * 单词测验接口
 *
 * 流程：start → 客户端按顺序作答逐题 submitAnswer → 客户端调用 finish 获取统计与错题回顾
 */
@RestController
@RequestMapping("/api/word-test")
@RequiredArgsConstructor
public class WordTestController {

    private final WordTestService wordTestService;

    /** 开启一次测验（按模式/来源/数量） */
    @PostMapping("/start")
    public Result<StartTestResp> start(@Valid @RequestBody StartTestReq req) {
        return Result.ok(wordTestService.start(UserContext.requireUserId(), req));
    }

    /** 提交一道题答案 */
    @PostMapping("/answer")
    public Result<Void> answer(@RequestBody java.util.Map<String, Object> body) {
        Long batchId = ((Number) body.get("batchId")).longValue();
        Long wordId = ((Number) body.get("wordId")).longValue();
        Integer correct = (Integer) body.get("correct");
        Integer costMs = body.get("costMs") == null ? null : ((Number) body.get("costMs")).intValue();
        String userAnswer = (String) body.get("userAnswer");
        wordTestService.submitAnswer(UserContext.requireUserId(), batchId, wordId,
                Integer.valueOf(1).equals(correct), costMs, userAnswer);
        return Result.ok();
    }

    /** 完成测验，返回统计与错题 */
    @PostMapping("/finish")
    public Result<FinishTestResp> finish(@RequestBody java.util.Map<String, Object> body) {
        Long batchId = ((Number) body.get("batchId")).longValue();
        return Result.ok(wordTestService.finish(UserContext.requireUserId(), batchId));
    }

    /** 历史轮次列表（已完成的，按时间倒序，带第几轮序号） */
    @GetMapping("/history")
    public Result<List<HistoryItemDto>> history() {
        return Result.ok(wordTestService.history(UserContext.requireUserId()));
    }

    /** 某一轮详情（逐题单词 + 用户作答 + 对错） */
    @GetMapping("/history/{batchId}")
    public Result<HistoryDetailDto> historyDetail(@PathVariable Long batchId) {
        return Result.ok(wordTestService.historyDetail(UserContext.requireUserId(), batchId));
    }
}