package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.dto.ChatRequest;
import com.example.feuerwerkagent.dto.ChatResponse;
import com.example.feuerwerkagent.dto.SearchRequest;
import com.example.feuerwerkagent.dto.SearchResponse;
import com.example.feuerwerkagent.service.ChatService;
import com.example.feuerwerkagent.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final ChatService chatService;
    private final SearchService searchService;

    public AIController(ChatService chatService, SearchService searchService) {
        this.chatService = chatService;
        this.searchService = searchService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        if (chatRequest.getMessage() == null || chatRequest.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Message cannot be empty"));
        }

        ChatResponse response = chatService.chat(chatRequest);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/chat/simple")
    public ResponseEntity<ChatResponse> simpleChat(@RequestBody java.util.Map<String, String> request) {
        try {
            String message = request.get("message");
            if (message == null || message.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ChatResponse.error("Message cannot be empty"));
            }

            ChatResponse response = chatService.simpleChat(message);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.error("Invalid request format: " + e.getMessage()));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@RequestBody SearchRequest searchRequest) {
        if (searchRequest.getQuery() == null || searchRequest.getQuery().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(SearchResponse.error("Query cannot be empty"));
        }

        if (searchRequest.getMaxResults() <= 0) {
            searchRequest.setMaxResults(10);
        }

        SearchResponse response = searchService.search(searchRequest);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/search/simple")
    public ResponseEntity<SearchResponse> simpleSearch(@RequestBody java.util.Map<String, String> request) {
        try {
            String query = request.get("query");
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(SearchResponse.error("Query cannot be empty"));
            }

            SearchResponse response = searchService.simpleSearch(query);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(SearchResponse.error("Invalid request format: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AI Agent is running!");
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        try {
            // 测试API连接
            String testResult = chatService.simpleChat("你好").getContent();
            return ResponseEntity.ok("API测试成功: " + testResult.substring(0, Math.min(100, testResult.length())));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("API测试失败: " + e.getMessage());
        }
    }
}
