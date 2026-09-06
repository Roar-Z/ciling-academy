package com.wordspirit.module.paper.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.paper.dto.PaperImportReq;
import com.wordspirit.module.paper.entity.ExercisePaper;
import com.wordspirit.module.paper.service.ExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 试卷导入接口（模式B：AI生成试卷 → 用户主动导入）
 *
 * 只有用户点击【导入为本套练习试卷】才提交原始JSON；
 * 后端严格 Schema 校验，非法则提示重新生成。
 */
@RestController
@RequestMapping("/api/paper")
@RequiredArgsConstructor
public class PaperGenerateAiController {

    private final ExerciseService exerciseService;

    /** 导入 AI 生成的试卷 */
    @PostMapping("/import")
    public Result<ExercisePaper> importPaper(@Valid @RequestBody PaperImportReq req) {
        return Result.ok(exerciseService.importFromAi(UserContext.requireUserId(), req.getJsonData()));
    }
}
