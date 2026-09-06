package com.wordspirit.module.translate.controller;

import com.wordspirit.common.Result;
import com.wordspirit.common.UserContext;
import com.wordspirit.module.translate.dto.TranslateReq;
import com.wordspirit.module.translate.dto.TranslateResp;
import com.wordspirit.module.translate.service.TranslateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 翻译接口
 */
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslateService translateService;

    @PostMapping
    public Result<TranslateResp> translate(@Valid @RequestBody TranslateReq req) {
        return Result.ok(translateService.translate(UserContext.requireUserId(), req));
    }
}