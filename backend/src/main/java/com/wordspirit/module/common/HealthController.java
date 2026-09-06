package com.wordspirit.module.common;

import com.wordspirit.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of("status", "up", "app", "word-spirit"));
    }
}
