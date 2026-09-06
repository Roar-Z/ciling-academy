package com.wordspirit.module.user.controller;

import com.wordspirit.common.Result;
import com.wordspirit.module.user.service.CaptchaService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/captcha")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaService captchaService;

    /** 图形验证码独立校验接口（先验图形，通过后才能发邮箱验证码） */
    @PostMapping("/verify")
    public Result<Void> verify(@RequestBody Map<String, String> body) {
        captchaService.verify(body.get("captchaId"), body.get("code"));
        return Result.ok();
    }

    /** 生成图形验证码（4位字符 + 干扰线），同时把 captchaId 写入 Cookie */
    @GetMapping("/generate")
    public Result<Map<String, Object>> generate(HttpServletResponse response) {
        Map<String, Object> data = captchaService.generate();
        String id = String.valueOf(data.get("captchaId"));
        // 写入非 HttpOnly cookie，前端通过 axios withCredentials 自动带回
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("captcha_id", id);
        cookie.setPath("/");
        cookie.setMaxAge(300); // 5 分钟
        cookie.setHttpOnly(false);
        // 显式加 SameSite=Lax，便于 axios withCredentials 通过
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
        log.info("[CaptchaController] 写入 cookie: captcha_id={}", id);
        return Result.ok(data);
    }
}