package com.example.feuerwerkagent.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON值转换器 - 用于处理Qdrant的payload
 */
@Slf4j
public class JsonValueConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将Map转换为Qdrant JSON格式
     */
    public static String convertToQdrantJson(Map<String, Object> payloadMap) {
        try {
            return objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            log.error("JSON转换失败", e);
            throw new RuntimeException("JSON转换失败", e);
        }
    }

    /**
     * 从Qdrant JSON解析为Map
     */
    public static Map<String, Object> convertFromQdrantJson(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            Map<String, Object> result = new HashMap<>();

            jsonNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isTextual()) {
                    result.put(key, valueNode.asText());
                } else if (valueNode.isInt()) {
                    result.put(key, valueNode.asInt());
                } else if (valueNode.isLong()) {
                    result.put(key, valueNode.asLong());
                } else if (valueNode.isDouble()) {
                    result.put(key, valueNode.asDouble());
                } else if (valueNode.isBoolean()) {
                    result.put(key, valueNode.asBoolean());
                } else if (valueNode.isObject() || valueNode.isArray()) {
                    result.put(key, valueNode.toString());
                }
            });

            return result;
        } catch (Exception e) {
            log.error("JSON解析失败", e);
            throw new RuntimeException("JSON解析失败", e);
        }
    }

    /**
     * 创建Qdrant payload对象
     */
    public static ObjectNode createPayloadObject() {
        return objectMapper.createObjectNode();
    }
}