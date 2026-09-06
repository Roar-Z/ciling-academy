package com.wordspirit;

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
        SpringApplication.run(WordSpiritApplication.class, args);
        System.out.println("""

            ============================================
            词灵学园 启动成功 (Word Spirit Academy)
            AI 增强型英语单词学习平台
            ============================================
            """);
    }
}
