package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.BgeEmbeddingConfig;
import com.example.feuerwerkagent.config.QdrantConfig;
import com.example.feuerwerkagent.service.BgeEmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 调试控制器 - 用于排查问题
 */
@Slf4j
@RestController
@RequestMapping("/api/debug")
@CrossOrigin(origins = "*")
public class DebugController {

    @Autowired
    private QdrantConfig qdrantConfig;

    @Autowired
    private BgeEmbeddingConfig bgeEmbeddingConfig;

    @Autowired
    private BgeEmbeddingService bgeEmbeddingService;

    /**
     * 检查向量配置一致性
     */
    @GetMapping("/vector-config")
    public Map<String, Object> checkVectorConfig() {
        Map<String, Object> result = new HashMap<>();

        // Qdrant配置
        int qdrantVectorSize = qdrantConfig.getVectorSize();
        result.put("qdrant_vector_size", qdrantVectorSize);
        result.put("qdrant_collection", qdrantConfig.getCollectionName());
        result.put("qdrant_host", qdrantConfig.getHost() + ":" + qdrantConfig.getPort());

        // BGE配置
        int bgeVectorSize = bgeEmbeddingConfig.getVectorSize();
        result.put("bge_vector_size", bgeVectorSize);
        result.put("bge_enabled", bgeEmbeddingConfig.isEnabled());
        result.put("bge_model_path", bgeEmbeddingConfig.getModelPath());

        // 一致性检查
        boolean configMatch = (qdrantVectorSize == bgeVectorSize);
        result.put("config_match", configMatch);

        if (!configMatch) {
            result.put("warning", "向量维度配置不匹配！Qdrant: " + qdrantVectorSize + ", BGE: " + bgeVectorSize);
        }

        // BGE服务状态
        result.put("bge_service_available", bgeEmbeddingService.isAvailable());

        return result;
    }

    /**
     * 测试向量生成
     */
    @PostMapping("/test-embedding")
    public Map<String, Object> testEmbedding(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String text = request.getOrDefault("text", "这是一个测试文本");

            // 生成测试向量
            float[] vector = bgeEmbeddingService.generateEmbedding(text);

            result.put("success", vector != null);
            result.put("vector_length", vector != null ? vector.length : 0);
            result.put("qdrant_expected", qdrantConfig.getVectorSize());
            result.put("vector_match", vector != null && vector.length == qdrantConfig.getVectorSize());

            if (vector != null && vector.length != qdrantConfig.getVectorSize()) {
                result.put("error", "向量维度不匹配！生成: " + vector.length + ", 期望: " + qdrantConfig.getVectorSize());
            }

            // 向量样本（前5个值）
            if (vector != null && vector.length > 0) {
                float[] sample = new float[Math.min(5, vector.length)];
                System.arraycopy(vector, 0, sample, 0, sample.length);
                result.put("vector_sample", sample);
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/system-status")
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        // Java版本
        status.put("java_version", System.getProperty("java.version"));
        status.put("os_name", System.getProperty("os.name"));
        status.put("os_arch", System.getProperty("os.arch"));

        // 内存信息
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;

        status.put("memory_max_mb", maxMemory);
        status.put("memory_total_mb", totalMemory);
        status.put("memory_free_mb", freeMemory);
        status.put("memory_used_mb", totalMemory - freeMemory);

        return status;
    }
}