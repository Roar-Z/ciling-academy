package com.wordspirit.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.wordspirit.config.AiGlobalConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Qdrant 向量检索服务（RAG 知识增强）
 *
 * 流程：
 * 1. 用通义千问 text-embedding-v3 模型把查询问题编码为向量
 * 2. 到云端 Qdrant 集合 ling-word-kb 中做余弦相似度检索，取 top4
 * 3. 返回知识点文本，供 system prompt 中的 {rag_context} 使用
 *
 * 降级策略：RAG 不可用时返回空列表，AI 仅凭模型通用知识回答，不影响主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagSearchService {

    private static final int TOP_K = 4;
    private static final int EMBED_DIM = 1024;

    private final AiGlobalConfig aiConfig;

    /**
     * 检索 top4 知识点文本
     *
     * @param query 用户问题
     * @return 知识点文本列表（可能为空）
     */
    public List<String> searchTopK(String query) {
        String qdrantUrl = aiConfig.getQdrant().getUrl();
        if (!aiConfig.isEnable()
                || StrUtil.isBlank(qdrantUrl)
                || qdrantUrl.contains("your-qdrant")) {
            return List.of();
        }
        try {
            float[] vector = embed(query);
            String searchBody = buildSearchBody(vector);
            JSONObject result = postJson(
                    StrUtil.removeSuffix(qdrantUrl, "/") + "/collections/"
                            + aiConfig.getQdrant().getCollection() + "/points/search",
                    searchBody);

            List<String> texts = new ArrayList<>(TOP_K);
            JSONArray points = result.getJSONArray("result");
            if (points != null) {
                for (int i = 0; i < points.size() && i < TOP_K; i++) {
                    JSONObject point = points.getJSONObject(i);
                    JSONObject payload = point.getJSONObject("payload");
                    if (payload != null) {
                        String text = payload.getStr("text");
                        if (StrUtil.isNotBlank(text)) {
                            texts.add(text);
                        }
                    }
                }
            }
            log.info("RAG检索完成，命中 {} 条知识", texts.size());
            return texts;
        } catch (Exception e) {
            log.warn("RAG检索失败，降级为通用知识回答: {}", e.getMessage());
            return List.of();
        }
    }

    /** 调用通义千问向量模型编码文本 */
    private float[] embed(String text) throws RuntimeException {
        JSONObject body = JSONUtil.createObj()
                .set("model", aiConfig.getQwen().getEmbeddingModel())
                .set("input", new JSONArray().add(text));
        JSONObject resp = postJson(
                StrUtil.removeSuffix(aiConfig.getQwen().getBaseUrl(), "/") + "/embeddings",
                body.toString());
        JSONArray data = resp.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new RuntimeException("embedding 结果为空");
        }
        JSONArray vector = data.getJSONObject(0).getJSONArray("embedding");
        float[] vec = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            vec[i] = vector.getFloat(i);
        }
        return vec;
    }

    /** 构造 Qdrant 检索请求体 */
    private String buildSearchBody(float[] vector) {
        JSONArray arr = new JSONArray();
        for (float v : vector) {
            arr.add(v);
        }
        return JSONUtil.createObj()
                .set("vector", arr)
                .set("limit", TOP_K)
                .set("with_payload", true)
                .toString();
    }

    /** 通用 JSON POST 请求 */
    private JSONObject postJson(String url, String body) {
        try (HttpResponse resp = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("api-key", aiConfig.getQdrant().getApiKey())
                .body(body)
                .timeout(8000)
                .execute()) {
            if (!resp.isOk()) {
                throw new RuntimeException("HTTP " + resp.getStatus() + " " + resp.body());
            }
            JSONObject obj = JSONUtil.parseObj(resp.body());
            if (obj.containsKey("error")) {
                throw new RuntimeException("API error: " + obj.getStr("error"));
            }
            return obj;
        }
    }
}
