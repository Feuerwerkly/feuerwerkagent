package com.example.feuerwerkagent.dto;

import lombok.Data;

@Data
public class KbSearchResult {
    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文档名称
     */
    private String fileName;

    /**
     * 文档块索引
     */
    private Integer chunkIndex;

    /**
     * 检索返回的片段内容
     */
    private String text;

    /**
     * 相似度分数
     */
    private Double score;

    public KbSearchResult() {
    }

    public KbSearchResult(Long documentId, String fileName, Integer chunkIndex, String text, Double score) {
        this.documentId = documentId;
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.text = text;
        this.score = score;
    }
}