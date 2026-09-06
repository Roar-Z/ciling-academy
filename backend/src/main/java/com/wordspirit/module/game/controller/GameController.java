package com.wordspirit.module.game.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.game.dto.GameRecordReq;
import com.wordspirit.module.game.service.GameService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 游戏接口
 */
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    /** 保存游戏记录并发放金币 */
    @PostMapping("/record")
    public Result<Map<String, Object>> saveRecord(@Valid @RequestBody GameRecordReq req) {
        return Result.ok(gameService.saveRecord(UserContext.requireUserId(), req));
    }

    /** 我的游戏记录 */
    @GetMapping("/records")
    public Result<Object> myRecords(String gameId, Long page, Long size) {
        return Result.ok(gameService.myRecords(UserContext.requireUserId(), gameId,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 简单排行榜 */
    @GetMapping("/rank")
    public Result<List<Map<String, Object>>> rank(String gameId, Integer limit) {
        return Result.ok(gameService.rank(gameId, limit == null ? 10 : limit));
    }

    /** 我的游戏统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(String gameId) {
        return Result.ok(gameService.myStats(UserContext.requireUserId(), gameId));
    }
}
