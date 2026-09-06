package com.wordspirit.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置
 *
 * 使用 Spring Security Crypto 的 BCryptPasswordEncoder：
 * - 只引入 spring-security-crypto 模块，不含 Security 过滤器链，不影响现有
 *   Redis Token 认证体系
 * - 生成的 hash 为 $2a$10$ 格式，与此前 hutool BCrypt 的存量密码互相兼容，
 *   无需任何数据迁移
 * - 每次加密自动加盐，同一密码每次 hash 不同，天然抗彩虹表/拖库比对
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
