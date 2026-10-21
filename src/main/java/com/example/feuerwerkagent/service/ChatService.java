package com.example.feuerwerkagent.service;

import com.example.feuerwerkagent.config.AnthropicConfig;
import com.example.feuerwerkagent.dto.ChatRequest;
import com.example.feuerwerkagent.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ChatService {

    private final AnthropicConfig anthropicConfig;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChatService(AnthropicConfig anthropicConfig) {
        this.anthropicConfig = anthropicConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)   // 增加连接超时
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)      // 增加读取超时到3分钟
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)                              // 失败重试
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public ChatResponse chat(ChatRequest chatRequest) {
        try {
            String requestBody = buildRequestBody(chatRequest);

            Request request = new Request.Builder()
                    .url(anthropicConfig.getBaseUrl() + "/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + anthropicConfig.getApiKey())
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ChatResponse.error("API request failed with status: " + response.code());
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (Exception e) {
            return ChatResponse.error("Error processing chat request: " + e.getMessage());
        }
    }

    public void streamChat(String message, List<Map<String, String>> conversationHistory, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("开始DeepSeek流式聊天，消息: " + message);

                // 发送开始事件
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("{\"status\":\"starting\"}"));

                String requestBody = buildStreamRequestBody(message, conversationHistory);
                System.out.println("流式请求体: " + requestBody);

                Request request = new Request.Builder()
                        .url(anthropicConfig.getBaseUrl() + "/v1/chat/completions")
                        .addHeader("Authorization", "Bearer " + anthropicConfig.getApiKey())
                        .addHeader("content-type", "application/json")
                        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                        .build();

                System.out.println("发送DeepSeek流式API请求");

                try (Response response = httpClient.newCall(request).execute()) {
                    System.out.println("DeepSeek API响应状态: " + response.code());

                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "无响应体";
                        System.err.println("DeepSeek API请求失败: " + response.code() + ", 响应: " + errorBody);
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"error\":\"API request failed: " + escapeJson(errorBody) + "\"}"));
                        emitter.complete();
                        return;
                    }

                    // 处理流式响应 - 使用 BufferedReader 逐行读取
                    ResponseBody body = response.body();
                    if (body != null) {
                        StringBuilder fullContent = new StringBuilder();
                        int chunkCount = 0;

                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(body.byteStream()))) {
                            String line;
                            System.out.println("开始读取流式响应");

                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data:")) {
                                    String jsonData = line.substring(5).trim();

                                    if ("[DONE]".equals(jsonData)) {
                                        System.out.println("收到流式完成标记");
                                        break;
                                    }

                                    try {
                                        JsonNode event = objectMapper.readTree(jsonData);

                                        // OpenAI 兼容格式：choices[].delta.content 和 reasoning_content
                                        JsonNode choices = event.get("choices");
                                        if (choices != null && choices.isArray() && choices.size() > 0) {
                                            JsonNode choice = choices.get(0);
                                            JsonNode delta = choice.get("delta");

                                            // 先检查 reasoning_content 字段（思考过程）
                                            if (delta != null && delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                                                String reasoningText = delta.get("reasoning_content").asText();
                                                if (!reasoningText.isEmpty()) {
                                                    chunkCount++;
                                                    System.out.println("收到思考块 #" + chunkCount + ": " + reasoningText);

                                                    // 发送思考过程，使用不同的事件名
                                                    emitter.send(SseEmitter.event()
                                                            .name("reasoning")
                                                            .data("{\"content\":\"" + escapeJson(reasoningText) + "\"}"));
                                                }
                                            }

                                            // 再检查 content 字段（最终回复）
                                            if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                                String text = delta.get("content").asText();
                                                if (!text.isEmpty()) {
                                                    fullContent.append(text);
                                                    chunkCount++;
                                                    System.out.println("收到回答块 #" + chunkCount + ": " + text);

                                                    // 发送正式回答内容
                                                    emitter.send(SseEmitter.event()
                                                            .name("content")
                                                            .data("{\"content\":\"" + escapeJson(text) + "\"}"));
                                                }
                                            }

                                            // 检查是否完成（finish_reason 非空表示结束）
                                            JsonNode finishReason = choice.get("finish_reason");
                                            if (finishReason != null && !finishReason.isNull() && !finishReason.asText().isEmpty()) {
                                                System.out.println("消息流完成，总长度: " + fullContent.length() + ", 块数: " + chunkCount);
                                                break;
                                            }
                                        }

                                    } catch (Exception e) {
                                        System.err.println("解析SSE事件失败: " + e.getMessage());
                                    }
                                }
                            }
                        }

                        System.out.println("流式发送完成");

                        // 将助手回复添加到对话历史
                        if (conversationHistory != null && fullContent.length() > 0) {
                            Map<String, String> assistantMsg = new HashMap<>();
                            assistantMsg.put("role", "assistant");
                            assistantMsg.put("content", fullContent.toString());
                            conversationHistory.add(assistantMsg);
                            System.out.println("对话历史已更新，当前轮数: " + conversationHistory.size());
                        }

                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data("{\"status\":\"done\"}"));
                    }

                    emitter.complete();
                }
            } catch (Exception e) {
                System.err.println("流式聊天异常: " + e.getMessage());
                e.printStackTrace();
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}"));
                    emitter.complete();
                } catch (IOException ioException) {
                    emitter.completeWithError(ioException);
                }
            }
        });
    }

    private String buildStreamRequestBody(String message, List<Map<String, String>> conversationHistory) throws Exception {
        var requestBody = new java.util.HashMap<String, Object>();
        requestBody.put("model", anthropicConfig.getModel());
        requestBody.put("max_tokens", anthropicConfig.getMaxTokens());
        requestBody.put("temperature", anthropicConfig.getTemperature());
        requestBody.put("stream", true); // 启用真正的DeepSeek流式响应

        // 使用对话历史
        List<java.util.Map<String, String>> messages = new ArrayList<>();

        // 添加历史消息（除了最后一条，因为那会是当前用户消息）
        if (conversationHistory != null && conversationHistory.size() > 1) {
            for (int i = 0; i < conversationHistory.size() - 1; i++) {
                Map<String, String> historyMsg = conversationHistory.get(i);
                var msgMap = new java.util.HashMap<String, String>();
                msgMap.put("role", historyMsg.get("role"));
                msgMap.put("content", historyMsg.get("content"));
                messages.add(msgMap);
            }
        }

        // 添加当前用户消息
        var msgMap = new java.util.HashMap<String, String>();
        msgMap.put("role", "user");
        msgMap.put("content", message);
        messages.add(msgMap);
        requestBody.put("messages", messages);

        return objectMapper.writeValueAsString(requestBody);
    }

    private String buildRequestBody(ChatRequest chatRequest) throws Exception {
        List<ChatRequest.Message> messages = new ArrayList<>();

        if (chatRequest.getConversationHistory() != null) {
            messages.addAll(chatRequest.getConversationHistory());
        }

        messages.add(new ChatRequest.Message("user", chatRequest.getMessage()));

        var requestBody = new java.util.HashMap<String, Object>();
        requestBody.put("model", anthropicConfig.getModel());
        requestBody.put("max_tokens", chatRequest.getMaxTokens() > 0 ? chatRequest.getMaxTokens() : anthropicConfig.getMaxTokens());
        requestBody.put("temperature", chatRequest.getTemperature() > 0 ? chatRequest.getTemperature() : anthropicConfig.getTemperature());

        List<java.util.Map<String, String>> apiMessages = new ArrayList<>();
        for (ChatRequest.Message msg : messages) {
            var msgMap = new java.util.HashMap<String, String>();
            msgMap.put("role", msg.getRole());
            msgMap.put("content", msg.getContent());
            apiMessages.add(msgMap);
        }
        requestBody.put("messages", apiMessages);

        return objectMapper.writeValueAsString(requestBody);
    }

    private ChatResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // OpenAI 兼容格式：choices[].message.content
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            String content = (message != null && message.has("content") && !message.get("content").isNull())
                    ? message.get("content").asText() : "";
            String model = root.has("model") ? root.get("model").asText() : anthropicConfig.getModel();
            int usage = 0;
            if (root.has("usage")) {
                JsonNode usageNode = root.get("usage");
                usage += usageNode.has("prompt_tokens") ? usageNode.get("prompt_tokens").asInt() : 0;
                usage += usageNode.has("completion_tokens") ? usageNode.get("completion_tokens").asInt() : 0;
            }

            return ChatResponse.success(content, model, usage);
        } else {
            return ChatResponse.error("Unexpected response format from API");
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    public ChatResponse simpleChat(String message) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        return chat(request);
    }
}
