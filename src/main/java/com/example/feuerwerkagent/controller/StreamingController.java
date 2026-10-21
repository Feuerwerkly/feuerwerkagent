package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.service.ChatService;
import com.example.feuerwerkagent.service.SearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai/stream")
public class StreamingController {

    private final ChatService chatService;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;
    // 简单的内存对话历史存储
    private final List<Map<String, String>> conversationHistory = new ArrayList<>();

    public StreamingController(ChatService chatService, SearchService searchService) {
        this.chatService = chatService;
        this.searchService = searchService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message) {
        System.out.println("🚀 收到DeepSeek流式聊天GET请求: " + message);

        if (message == null || message.trim().isEmpty()) {
            System.err.println("❌ 消息为空");
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"Message cannot be empty\"}"));
                    errorEmitter.complete();
                } catch (IOException e) {
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }

        System.out.println("✅ 开始处理DeepSeek流式聊天: " + message);

        // 添加用户消息到历史
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        conversationHistory.add(userMsg);

        SseEmitter emitter = new SseEmitter(120000L);
        chatService.streamChat(message, conversationHistory, emitter);

        return emitter;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChatPost(@RequestBody Map<String, String> request) {
        System.out.println("🚀 收到DeepSeek流式聊天POST请求: " + request);

        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            System.err.println("❌ 消息为空");
            SseEmitter errorEmitter = new SseEmitter();
            CompletableFuture.runAsync(() -> {
                try {
                    errorEmitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"Message cannot be empty\"}"));
                    errorEmitter.complete();
                } catch (IOException e) {
                    errorEmitter.completeWithError(e);
                }
            });
            return errorEmitter;
        }

        System.out.println("✅ 开始处理DeepSeek流式聊天: " + message);

        // 添加用户消息到历史
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        conversationHistory.add(userMsg);

        SseEmitter emitter = new SseEmitter(120000L);
        chatService.streamChat(message, conversationHistory, emitter);

        return emitter;
    }

    @PostMapping("/clear-history")
    public Map<String, String> clearConversationHistory() {
        System.out.println("🗑️ 清空对话历史");
        conversationHistory.clear();
        return Map.of("status", "success", "message", "Conversation history cleared");
    }

    @PostMapping(value = "/search", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSearch(@RequestBody Map<String, String> request) {
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时
        String query = request.get("query");

        CompletableFuture.runAsync(() -> {
            try {
                // 发送开始事件
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("{\"status\":\"starting\"}"));

                // 发送搜索状态
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data("{\"message\":\"正在搜索网络...\"}"));

                // 执行搜索
                var searchResponse = searchService.simpleSearch(query);

                if (searchResponse.isSuccess()) {
                    // 发送搜索结果
                    emitter.send(SseEmitter.event()
                            .name("results")
                            .data(objectMapper.writeValueAsString(searchResponse)));

                    // 发送AI摘要状态
                    emitter.send(SseEmitter.event()
                            .name("status")
                            .data("{\"message\":\"正在生成AI摘要...\"}"));

                    // 模拟流式AI摘要
                    String aiSummary = searchResponse.getAiSummary();
                    if (aiSummary != null) {
                        int chunkSize = 30;
                        for (int i = 0; i < aiSummary.length(); i += chunkSize) {
                            int end = Math.min(i + chunkSize, aiSummary.length());
                            String chunk = aiSummary.substring(i, end);

                            emitter.send(SseEmitter.event()
                                    .name("summary")
                                    .data("{\"content\":\"" + escapeJson(chunk) + "\"}"));

                            Thread.sleep(80);
                        }
                    }

                    // 发送完成事件
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("{\"status\":\"done\"}"));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + escapeJson(searchResponse.getError()) + "\"}"));
                }

                emitter.complete();
            } catch (Exception e) {
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

        return emitter;
    }

    private String simulateStreamingChat(String message) {
        try {
            var response = chatService.simpleChat(message);
            if (response.isSuccess()) {
                return response.getContent();
            } else {
                return "抱歉，处理您的请求时出现错误：" + response.getError();
            }
        } catch (Exception e) {
            return "系统错误：" + e.getMessage();
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
}
