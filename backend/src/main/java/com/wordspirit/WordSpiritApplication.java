package com.wordspirit;

import java.util.TimeZone;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 词灵学园 - 英语单词学习平台 启动类
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.wordspirit.module.**.mapper")
public class WordSpiritApplication {

    public static void main(String[] args) {
        // 强制 JVM 默认时区为北京时间：部署环境（香港服务器/容器）默认时区是 UTC，
        // 会导致 LocalDateTime.now()/LocalDate.now() 少 8 小时——
        // 表现为「新词学习点模糊/不记得后，下次复习时间比当前时间还早」、
        // 任务/签到/统计按 UTC 提前 8 小时切日。必须在 Spring 启动前设置。
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(WordSpiritApplication.class, args);
        System.out.println("""

            ============================================
            词灵学园 启动成功 (Word Spirit Academy)
            AI 增强型英语单词学习平台
            ============================================
            """);
    }
}
