package com.example.feuerwerkagent.dto;

import lombok.Data;

/**
 * 文档处理进度信息
 */
@Data
public class KbDocumentProgress {

    /**
     * 文档ID
     */
    private Long documentId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 当前状态：uploading, parsing, chunking, embedding, storing, completed, failed
     */
    private String status;

    /**
     * 进度百分比：0-100
     */
    private int progress;

    /**
     * 当前步骤描述
     */
    private String currentStep;

    /**
     * 错误信息（如果有）
     */
    private String errorMessage;

    /**
     * 处理开始时间
     */
    private Long startTime;

    /**
     * 预估剩余时间（秒）
     */
    private Long estimatedSecondsRemaining;

    /**
     * 已处理的文本块数量
     */
    private Integer processedChunks;

    /**
     * 总文本块数量
     */
    private Integer totalChunks;
}