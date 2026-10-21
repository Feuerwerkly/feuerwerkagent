package com.example.feuerwerkagent.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class EmbeddingConfig {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.base-url}")
    private String baseUrl;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.temperature:0.7}")
    private double temperature;

    @Value("${anthropic.max-tokens:4096}")
    private int maxTokens;

    @Value("${anthropic.enable-embedding:true}")
    private boolean enableEmbedding;

    /**
     * 获取嵌入API的URL
     * DeepSeek 等模型可能使用不同的端点
     */
    public String getEmbeddingUrl() {
        // 对于自定义API，使用通用的embeddings端点
        if (baseUrl.contains("datagrand.com")) {
            // DataGrand 平台可能使用不同的端点格式
            return baseUrl + "/v1/embeddings";
        }
        return baseUrl + "/v1/embeddings";
    }

    /**
     * 检查是否有可用的嵌入API配置
     */
    public boolean hasValidConfig() {
        return enableEmbedding && apiKey != null && !apiKey.isEmpty() &&
               !apiKey.startsWith("${") &&
               baseUrl != null && !baseUrl.isEmpty();
    }

    /**
     * 检查是否启用了API嵌入调用
     */
    public boolean isEmbeddingEnabled() {
        return enableEmbedding;
    }
}