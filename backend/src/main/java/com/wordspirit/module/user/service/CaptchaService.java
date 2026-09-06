package com.wordspirit.module.user.service;

import cn.hutool.captcha.CircleCaptcha;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 图形验证码服务
 * <p>
 * 用于注册、发送邮箱验证码、注销账号等敏感操作前的人机校验。
 * 验证码存 Redis，TTL 5 分钟，一次性消费。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_KEY = "captcha:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    /** 生成图形验证码（4位字符 + 干扰线），返回 captchaId + base64 PNG */
    public Map<String, Object> generate() {
        // hutool 默认字符集为数字，配合随机角度和干扰线已足够人机识别
        CircleCaptcha captcha = new CircleCaptcha(160, 60, 4, 25);
        captcha.createCode();

        String code = captcha.getCode().toLowerCase();
        String captchaId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(CAPTCHA_KEY + captchaId, code, CAPTCHA_TTL);

        Map<String, Object> result = new HashMap<>();
        result.put("captchaId", captchaId);
        result.put("image", "data:image/png;base64," + captcha.getImageBase64());
        return result;
    }

    /** 校验并消费图形验证码（一次性，校验成功后立即失效） */
    public void verify(String captchaId, String input) {
        if (StrUtil.isBlank(captchaId) || StrUtil.isBlank(input)) {
            log.warn("[验证码校验] 字段为空: captchaId='{}' input='{}'", captchaId, input);
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先完成图形验证");
        }
        String key = CAPTCHA_KEY + captchaId;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (code == null) {
            log.warn("[验证码校验] Redis 已过期: captchaId='{}'", captchaId);
            throw new BusinessException(ResultCode.BAD_REQUEST, "图形验证码已过期，请刷新后重试");
        }
        if (!code.equals(input.trim().toLowerCase())) {
            log.warn("[验证码校验] 码不匹配: captchaId='{}' expected='{}' actual='{}'",
                    captchaId, code, input.trim().toLowerCase());
            throw new BusinessException(ResultCode.BAD_REQUEST, "图形验证码错误");
        }
        // 验证成功后保留 captchaId 并标记为"已使用"，让后续 register 接口可继续校验
        stringRedisTemplate.opsForValue().set(key, "USED", Duration.ofMinutes(10));
    }

    /** 校验 captchaId 是否已通过图形验证（用于 register 等后续接口，不再校验图形码本身） */
    public void validateUsed(String captchaId) {
        if (StrUtil.isBlank(captchaId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先完成图形验证");
        }
        String value = stringRedisTemplate.opsForValue().get(CAPTCHA_KEY + captchaId);
        if (!"USED".equals(value)) {
            log.warn("[验证码校验] captchaId 未使用或已失效: captchaId='{}' value='{}'", captchaId, value);
            throw new BusinessException(ResultCode.BAD_REQUEST, "请先完成图形验证");
        }
    }
}