package com.wordspirit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 词灵AI 全局配置
 * 读取 application.yml 中 ai.* 配置；大模型调用由 AiService 直接用 Hutool HTTP 完成，
 * 无需 Spring AI 依赖，避免版本/jar 下载问题。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiGlobalConfig {

    /** AI 总开关：false 时所有 AI 功能优雅降级，不影响基础学习功能 */
    private boolean enable = true;

    /** 每人每日 AI 调用额度 */
    private int dailyQuota = 10;

    /** 单次大模型调用超时时间（毫秒） */
    private long timeout = 45000;

    /** 失败重试次数 */
    private int retryTimes = 1;

    /** 通义千问大模型配置（阿里云百炼 DashScope，OpenAI 兼容） */
    private Qwen qwen = new Qwen();

    /** Qdrant 向量库配置 */
    private Qdrant qdrant = new Qdrant();

    @Data
    public static class Qwen {
        /** 阿里云百炼(DashScope) API Key */
        private String apiKey;
        /** OpenAI 兼容接口地址 */
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        /** 对话模型名（qwen-flash 免费省 token；也可换 qwen-turbo） */
        private String model = "qwen-flash";
        /** 向量模型名（用于 RAG 检索） */
        private String embeddingModel = "text-embedding-v3";

        /** 深度思考模型名（需支持 enable_thinking，如 qwen3 系列思考模型） */
        private String thinkingModel = "qwen3-30b-a3b-thinking-2507";
        /** 深度思考时的最大输出 token（思维链会占用输出额度，需大于常规） */
        private int thinkingMaxTokens = 4096;
        /** 深度思考能力总开关：false 时前端开关不切换模型，降级为常规模型 */
        private boolean thinkingEnabled = true;

        /** 联网搜索模型名（需支持 enable_search，如 qwen-plus） */
        private String searchModel = "qwen-plus";
        /** 联网搜索能力总开关：false 时前端开关不生效 */
        private boolean searchEnabled = true;
    }

    @Data
    public static class Qdrant {
        /** 云端实例地址，例如 https://xxx.cloud.qdrant.io */
        private String url;
        private String apiKey;
        /** 知识点集合名 */
        private String collection = "ling-word-kb";
    }
}