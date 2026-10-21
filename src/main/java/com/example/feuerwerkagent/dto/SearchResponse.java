package com.example.feuerwerkagent.dto;

import lombok.Data;
import java.util.List;

@Data
public class SearchResponse {
    private String query;
    private List<SearchResult> results;
    private String aiSummary;
    private boolean success;
    private String error;

    @Data
    public static class SearchResult {
        private String title;
        private String url;
        private String snippet;
        private String source;
    }

    public static SearchResponse success(String query, List<SearchResult> results, String aiSummary) {
        SearchResponse response = new SearchResponse();
        response.setQuery(query);
        response.setResults(results);
        response.setAiSummary(aiSummary);
        response.setSuccess(true);
        return response;
    }

    public static SearchResponse error(String error) {
        SearchResponse response = new SearchResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}
