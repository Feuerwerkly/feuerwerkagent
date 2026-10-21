package com.example.feuerwerkagent.service;

import com.example.feuerwerkagent.dto.KbDocumentProgress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档处理进度管理服务
 */
@Slf4j
@Service
public class KbProgressService {

    // 存储文档处理进度
    private final Map<Long, KbDocumentProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * 创建新的进度记录
     */
    public KbDocumentProgress createProgress(Long documentId, String fileName) {
        KbDocumentProgress progress = new KbDocumentProgress();
        progress.setDocumentId(documentId);
        progress.setFileName(fileName);
        progress.setStatus("uploading");
        progress.setProgress(0);
        progress.setCurrentStep("准备上传文件");
        progress.setStartTime(Instant.now().getEpochSecond());
        progress.setProcessedChunks(0);
        progress.setTotalChunks(null);

        progressMap.put(documentId, progress);
        log.info("创建进度记录: documentId={}, fileName={}", documentId, fileName);

        return progress;
    }

    /**
     * 更新进度
     */
    public void updateProgress(Long documentId, String status, int progress, String currentStep) {
        KbDocumentProgress docProgress = progressMap.get(documentId);
        if (docProgress != null) {
            docProgress.setStatus(status);
            docProgress.setProgress(progress);
            docProgress.setCurrentStep(currentStep);

            // 估算剩余时间
            if (progress > 0 && docProgress.getStartTime() != null) {
                long elapsedSeconds = Instant.now().getEpochSecond() - docProgress.getStartTime();
                long estimatedTotal = (elapsedSeconds * 100) / progress;
                long remainingSeconds = estimatedTotal - elapsedSeconds;
                docProgress.setEstimatedSecondsRemaining(Math.max(0, remainingSeconds));
            }

            log.debug("进度更新: documentId={}, status={}, progress={}%, step={}",
                    documentId, status, progress, currentStep);
        }
    }

    /**
     * 更新块处理进度
     */
    public void updateChunkProgress(Long documentId, int processedChunks, int totalChunks) {
        KbDocumentProgress progress = progressMap.get(documentId);
        if (progress != null) {
            progress.setProcessedChunks(processedChunks);
            progress.setTotalChunks(totalChunks);

            if (totalChunks > 0) {
                int chunkProgress = (int) ((processedChunks * 100) / totalChunks);
                updateProgress(documentId, progress.getStatus(), chunkProgress,
                        String.format("处理文本块 %d/%d", processedChunks, totalChunks));
            }
        }
    }

    /**
     * 设置错误状态
     */
    public void setError(Long documentId, String errorMessage) {
        KbDocumentProgress progress = progressMap.get(documentId);
        if (progress != null) {
            progress.setStatus("failed");
            progress.setProgress(0);
            progress.setCurrentStep("处理失败");
            progress.setErrorMessage(errorMessage);
            log.error("文档处理失败: documentId={}, error={}", documentId, errorMessage);
        }
    }

    /**
     * 设置完成状态
     */
    public void setCompleted(Long documentId) {
        KbDocumentProgress progress = progressMap.get(documentId);
        if (progress != null) {
            progress.setStatus("completed");
            progress.setProgress(100);
            progress.setCurrentStep("处理完成");
            progress.setEstimatedSecondsRemaining(0L);

            long elapsedSeconds = Instant.now().getEpochSecond() - progress.getStartTime();
            log.info("文档处理完成: documentId={}, 耗时={}秒", documentId, elapsedSeconds);
        }
    }

    /**
     * 获取进度信息
     */
    public KbDocumentProgress getProgress(Long documentId) {
        return progressMap.get(documentId);
    }

    /**
     * 删除进度记录
     */
    public void removeProgress(Long documentId) {
        progressMap.remove(documentId);
    }

    /**
     * 清理过期的进度记录（超过1小时）
     */
    public void cleanupExpiredProgress() {
        Instant oneHourAgo = Instant.now().minus(Duration.ofHours(1));

        progressMap.entrySet().removeIf(entry -> {
            KbDocumentProgress progress = entry.getValue();
            if (progress.getStartTime() != null) {
                Instant startTime = Instant.ofEpochSecond(progress.getStartTime());
                if (startTime.isBefore(oneHourAgo)) {
                    log.info("清理过期进度记录: documentId={}", entry.getKey());
                    return true;
                }
            }
            return false;
        });
    }
}