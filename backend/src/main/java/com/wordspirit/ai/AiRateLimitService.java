package com.wordspirit.ai;

import cn.hutool.core.date.DateUtil;
import com.wordspirit.common.ResultCode;
import com.wordspirit.config.AiGlobalConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * AI 限流服务：
 * 1. 系统级 QPS 限流（全站每秒最多 SYS_QPS 次调用，防止大模型被打爆）
 * 2. 用户级短时间冷却（同一用户两次 AI 调用间隔至少 USER_COOL_DOWN 秒）
 */
@Service
@RequiredArgsConstructor
public class AiRateLimitService {

    /** 系统每秒最大调用次数 */
    private static final long SYS_QPS = 20;
    /** 用户调用冷却时间（秒） */
    private static final long USER_COOL_DOWN = 3;

    private static final String SYS_KEY = "ai:sys:sec:{}";
    private static final String USER_COOL_KEY = "ai:cool:{}";

    private final AiGlobalConfig aiConfig;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 限流校验，通过则标记一次 */
    public void checkAndMark(Long userId) {
        if (!aiConfig.isEnable()) {
            throw new AiException(ResultCode.AI_UNAVAILABLE);
        }
        // 系统 QPS 限流
        String secKey = cn.hutool.core.text.StrFormatter.format(SYS_KEY, DateUtil.format(DateUtil.date(), "yyyyMMddHHmmss"));
        Long count = redisTemplate.opsForValue().increment(secKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(secKey, Duration.ofSeconds(2));
        }
        if (count != null && count > SYS_QPS) {
            throw new AiException(ResultCode.AI_RATE_LIMITED);
        }
        // 用户冷却：setIfAbsent 成功代表本次通过
        Boolean allowed = redisTemplate.opsForValue()
                .setIfAbsent(USER_COOL_KEY + userId, "1", Duration.ofSeconds(USER_COOL_DOWN));
        if (Boolean.FALSE.equals(allowed)) {
            throw new AiException(ResultCode.AI_RATE_LIMITED);
        }
    }
}
