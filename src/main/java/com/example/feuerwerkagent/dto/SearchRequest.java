package com.example.feuerwerkagent.dto;

import lombok.Data;

@Data
public class SearchRequest {
    private String query;
    private int maxResults;
    private boolean enableAI;
}
