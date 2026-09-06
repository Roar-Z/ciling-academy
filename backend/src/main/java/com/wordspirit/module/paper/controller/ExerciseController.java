package com.wordspirit.module.paper.controller;

import com.wordspirit.common.PageResult;
import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.paper.dto.SubmitReq;
import com.wordspirit.module.paper.dto.SubmitResult;
import com.wordspirit.module.paper.service.ExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 练习试卷接口（做题、批改、错题收录）
 */
@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    /** 试卷分页列表 */
    @GetMapping("/list")
    public Result<Object> list(String keyword, Long page, Long size) {
        return Result.ok(exerciseService.list(UserContext.requireUserId(), keyword,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 试卷详情（做卷不带答案；带 withAnswer=true 时用于回看） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, Boolean withAnswer) {
        return Result.ok(exerciseService.detail(UserContext.requireUserId(), id,
                Boolean.TRUE.equals(withAnswer)));
    }

    /** 交卷批改（Java 后端判分） */
    @PostMapping("/submit")
    public Result<SubmitResult> submit(@Valid @RequestBody SubmitReq req) {
        return Result.ok(exerciseService.submit(UserContext.requireUserId(), req));
    }

    /** 最近一次作答结果回看 */
    @GetMapping("/{id}/result")
    public Result<SubmitResult> lastResult(@PathVariable Long id) {
        return Result.ok(exerciseService.lastResult(UserContext.requireUserId(), id));
    }

    /** 错题收录（分页） */
    @GetMapping("/wrong")
    public Result<PageResult<Map<String, Object>>> wrongList(String qType, Long page, Long size) {
        return Result.ok(exerciseService.wrongList(UserContext.requireUserId(), qType,
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 从错题本移除一道错题 */
    @DeleteMapping("/wrong/{questionId}")
    public Result<Void> removeWrong(@PathVariable Long questionId) {
        exerciseService.removeWrong(UserContext.requireUserId(), questionId);
        return Result.ok();
    }

    /** 批量从错题本移除（整页选择删除） */
    @DeleteMapping("/wrong")
    public Result<Void> removeWrongBatch(@RequestBody Map<String, List<Long>> body) {
        exerciseService.removeWrongBatch(UserContext.requireUserId(), body.get("questionIds"));
        return Result.ok();
    }

    /** 删除试卷 */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        exerciseService.remove(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
