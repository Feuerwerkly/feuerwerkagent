package com.example.feuerwerkagent.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChatRequest {
    private String message;
    private List<Message> conversationHistory;
    private double temperature;
    private int maxTokens;

    @Data
    public static class Message {
        private String role;
        private String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
