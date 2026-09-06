package com.wordspirit.config;

import cn.hutool.core.util.StrUtil;
import com.wordspirit.common.BusinessException;
import com.wordspirit.common.ResultCode;
import com.wordspirit.common.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录认证拦截器：
 * 基于 Redis 令牌校验登录状态，校验通过后把 userId 写入 UserContext
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "Authorization";
    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String REDIS_TOKEN_KEY = "auth:token:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 兼容跨域场景：解析 header 或 query 中的 token
        String token = request.getHeader(TOKEN_HEADER);
        if (StrUtil.isNotBlank(token) && token.startsWith(TOKEN_PREFIX)) {
            token = token.substring(TOKEN_PREFIX.length());
        }
        if (StrUtil.isBlank(token)) {
            token = request.getParameter("token");
        }
        if (StrUtil.isBlank(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String userId = stringRedisTemplate.opsForValue().get(REDIS_TOKEN_KEY + token);
        if (StrUtil.isBlank(userId)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        // 续期 7 天
        stringRedisTemplate.expire(REDIS_TOKEN_KEY + token, java.time.Duration.ofDays(7));
        UserContext.setUserId(Long.valueOf(userId));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
