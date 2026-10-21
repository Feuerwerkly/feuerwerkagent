package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.dto.KbDocumentProgress;
import com.example.feuerwerkagent.dto.KbSearchRequest;
import com.example.feuerwerkagent.dto.KbSearchResult;
import com.example.feuerwerkagent.entity.KbDocument;
import com.example.feuerwerkagent.service.KbProgressService;
import com.example.feuerwerkagent.service.KbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/kb")
@CrossOrigin(origins = "*")
public class KbController {

    @Autowired
    private KbService kbService;

    @Autowired
    private KbProgressService progressService;

    /**
     * 文件上传接口
     */
    @PostMapping("/document/upload")
    public ApiResponse<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            log.info("收到文件上传请求: fileName={}, size={}", file.getOriginalFilename(), file.getSize());

            Long documentId = kbService.uploadDocument(file);

            Map<String, Object> data = new HashMap<>();
            data.put("documentId", documentId);

            return ApiResponse.success("上传成功，后台异步处理文档", data);

        } catch (IllegalArgumentException e) {
            log.error("文件上传参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ApiResponse.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 查询文档列表接口
     */
    @GetMapping("/document/list")
    public ApiResponse<List<KbDocument>> getDocumentList() {
        try {
            List<KbDocument> documents = kbService.getAllDocuments();
            return ApiResponse.success(documents);
        } catch (Exception e) {
            log.error("查询文档列表失败", e);
            return ApiResponse.error("查询文档列表失败: " + e.getMessage());
        }
    }

    /**
     * 知识库检索接口
     */
    @PostMapping("/search")
    public ApiResponse<List<KbSearchResult>> search(@RequestBody KbSearchRequest request) {
        try {
            log.info("收到知识库检索请求: query={}, documentIds={}, topN={}",
                    request.getQuery(),
                    request.getDocumentIds() != null ? java.util.Arrays.toString(request.getDocumentIds()) : "全部",
                    request.getTopN());

            List<KbSearchResult> results = kbService.search(request);
            return ApiResponse.success(results);

        } catch (IllegalArgumentException e) {
            log.error("检索参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("知识库检索失败", e);
            return ApiResponse.error("知识库检索失败: " + e.getMessage());
        }
    }

    /**
     * 删除文档接口
     */
    @DeleteMapping("/document/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long documentId) {
        try {
            log.info("收到删除文档请求: documentId={}", documentId);

            kbService.deleteDocument(documentId);
            return ApiResponse.success("文档删除成功", null);

        } catch (IllegalArgumentException e) {
            log.error("删除文档参数错误: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return ApiResponse.error("删除文档失败: " + e.getMessage());
        }
    }

    /**
     * 重新处理失败的文档
     */
    @PostMapping("/document/{documentId}/retry")
    public ApiResponse<Void> retryDocument(@PathVariable Long documentId) {
        try {
            log.info("收到重新处理文档请求: documentId={}", documentId);

            kbService.processDocumentAsync(documentId);
            return ApiResponse.success("开始重新处理文档", null);

        } catch (Exception e) {
            log.error("重新处理文档失败", e);
            return ApiResponse.error("重新处理文档失败: " + e.getMessage());
        }
    }

    /**
     * 查询文档处理进度
     */
    @GetMapping("/document/{documentId}/progress")
    public ApiResponse<KbDocumentProgress> getDocumentProgress(@PathVariable Long documentId) {
        try {
            log.debug("查询文档处理进度: documentId={}", documentId);

            KbDocumentProgress progress = progressService.getProgress(documentId);
            if (progress == null) {
                return ApiResponse.error(404, "文档不存在或处理已完成并清理");
            }

            return ApiResponse.success(progress);

        } catch (Exception e) {
            log.error("查询文档处理进度失败", e);
            return ApiResponse.error("查询文档处理进度失败: " + e.getMessage());
        }
    }

    /**
     * 批量查询文档处理进度
     */
    @PostMapping("/document/progress/batch")
    public ApiResponse<Map<Long, KbDocumentProgress>> getBatchProgress(@RequestBody Long[] documentIds) {
        try {
            Map<Long, KbDocumentProgress> progressMap = new HashMap<>();

            for (Long documentId : documentIds) {
                KbDocumentProgress progress = progressService.getProgress(documentId);
                if (progress != null) {
                    progressMap.put(documentId, progress);
                }
            }

            return ApiResponse.success(progressMap);

        } catch (Exception e) {
            log.error("批量查询文档处理进度失败", e);
            return ApiResponse.error("批量查询文档处理进度失败: " + e.getMessage());
        }
    }

    /**
     * 检查Qdrant状态和数据量
     */
    @GetMapping("/status/qdrant")
    public ApiResponse<Map<String, Object>> checkQdrantStatus() {
        try {
            log.info("检查Qdrant状态");

            Map<String, Object> status = kbService.checkQdrantStatus();
            return ApiResponse.success(status);

        } catch (Exception e) {
            log.error("检查Qdrant状态失败", e);
            return ApiResponse.error("检查Qdrant状态失败: " + e.getMessage());
        }
    }

    /**
     * 测试检索功能（用于调试）
     */
    @PostMapping("/test/search")
    public ApiResponse<Map<String, Object>> testSearch(@RequestBody Map<String, Object> params) {
        try {
            log.info("测试检索功能: {}", params);

            String query = (String) params.getOrDefault("query", "测试查询");
            int topN = ((Number) params.getOrDefault("topN", 5)).intValue();
            double minScore = ((Number) params.getOrDefault("minScore", 0.1)).doubleValue();

            // 创建搜索请求
            KbSearchRequest request = new KbSearchRequest();
            request.setQuery(query);
            request.setTopN(topN);
            request.setMinScore(minScore);

            // 执行搜索
            List<KbSearchResult> results = kbService.search(request);

            // 构建详细结果
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("resultCount", results.size());
            response.put("searchParams", Map.of(
                    "topN", topN,
                    "minScore", minScore
            ));
            response.put("results", results);

            return ApiResponse.success(response);

        } catch (Exception e) {
            log.error("测试检索失败", e);
            return ApiResponse.error("测试检索失败: " + e.getMessage());
        }
    }
}