package com.example.feuerwerkagent.dto;

import lombok.Data;

@Data
public class KbUploadRequest {
    private String query;
    private Long[] documentIds;
    private Integer topN = 5;
}