package com.wordspirit.module.learnround.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.learnround.dto.FinishLearnRoundReq;
import com.wordspirit.module.learnround.dto.LearnRoundDetailDto;
import com.wordspirit.module.learnround.dto.LearnRoundDto;
import com.wordspirit.module.learnround.service.LearnRoundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/learn-round")
@RequiredArgsConstructor
public class LearnRoundController {

    private final LearnRoundService learnRoundService;

    /** 完成一轮学习后持久化 */
    @PostMapping("/finish")
    public Result<LearnRoundDto> finish(@RequestBody FinishLearnRoundReq req) {
        return Result.ok(learnRoundService.finishRound(UserContext.requireUserId(), req));
    }

    /** 历史学习轮次（已完成，按时间倒序，带第几轮） */
    @GetMapping("/history")
    public Result<List<LearnRoundDto>> history() {
        return Result.ok(learnRoundService.history(UserContext.requireUserId()));
    }

    /** 某一轮详情（逐词 + 是否掌握） */
    @GetMapping("/{roundId}")
    public Result<LearnRoundDetailDto> detail(@PathVariable Long roundId) {
        return Result.ok(learnRoundService.detail(UserContext.requireUserId(), roundId));
    }

    /** 删除单个学习轮次（同时删 LearnRoundItem） */
    @DeleteMapping("/{roundId}")
    public Result<Void> delete(@PathVariable Long roundId) {
        learnRoundService.deleteRound(UserContext.requireUserId(), roundId);
        return Result.ok();
    }
}
