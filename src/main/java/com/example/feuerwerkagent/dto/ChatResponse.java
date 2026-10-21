package com.example.feuerwerkagent.dto;

import lombok.Data;

@Data
public class ChatResponse {
    private String content;
    private String model;
    private int usage;
    private boolean success;
    private String error;

    public static ChatResponse success(String content, String model, int usage) {
        ChatResponse response = new ChatResponse();
        response.setContent(content);
        response.setModel(model);
        response.setUsage(usage);
        response.setSuccess(true);
        return response;
    }

    public static ChatResponse error(String error) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}
