package com.example.feuerwerkagent.dto;

import lombok.Data;

@Data
public class KbSearchRequest {
    /**
     * 检索问题
     */
    private String query;

    /**
     * 勾选的文档ID数组（可选；不传则检索全部文档）
     */
    private Long[] documentIds;

    /**
     * 返回结果数量，默认5
     */
    private Integer topN = 5;

    /**
     * 最小相似度阈值，默认0.6
     */
    private Double minScore = 0.6;
}