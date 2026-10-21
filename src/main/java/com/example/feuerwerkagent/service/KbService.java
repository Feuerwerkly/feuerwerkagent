package com.example.feuerwerkagent.service;

import com.example.feuerwerkagent.config.EmbeddingConfig;
import com.example.feuerwerkagent.config.QdrantConfig;
import com.example.feuerwerkagent.dto.KbSearchRequest;
import com.example.feuerwerkagent.dto.KbSearchResult;
import com.example.feuerwerkagent.entity.KbDocument;
import com.example.feuerwerkagent.mapper.KbDocumentMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 知识库服务 - 支持进度追踪
 */
@Slf4j
@Service
public class KbService {

    @Autowired
    private QdrantConfig qdrantConfig;

    @Autowired
    private EmbeddingConfig embeddingConfig;

    @Autowired
    private BgeEmbeddingService bgeEmbeddingService;

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private KbProgressService progressService;

    @Autowired
    private KbDocumentMapper kbDocumentMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 上传文档（优化内存管理）
     */
    public Long uploadDocument(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 检查文件大小，防止内存溢出
        long fileSize = file.getSize();
        if (fileSize > 25 * 1024 * 1024) { // 25MB限制
            throw new IllegalArgumentException("文件大小超过限制（最大25MB），当前文件: " +
                    (fileSize / 1024.0 / 1024.0) + "MB");
        }

        String originalFilename = file.getOriginalFilename();
        Long documentId = System.currentTimeMillis();

        log.info("开始上传文档: fileName={}, size={}MB", originalFilename,
                fileSize / 1024.0 / 1024.0);

        // 创建进度记录
        progressService.createProgress(documentId, originalFilename);
        progressService.updateProgress(documentId, "uploading", 10, "保存文件到磁盘");

        try {
            // 保存文件
            String fileExtension = getFileExtension(originalFilename);
            String relativePath = saveFile(file, documentId, fileExtension);

            // 在数据库中插入文档记录
            KbDocument kbDocument = new KbDocument();
            kbDocument.setId(documentId);
            kbDocument.setFileName(originalFilename);
            kbDocument.setFileRelativePath(relativePath);
            kbDocument.setFileSize(file.getSize());
            kbDocument.setFileType(fileExtension);
            kbDocument.setStatus(KbDocument.Status.PENDING.getCode());
            kbDocument.setCreateTime(LocalDateTime.now());
            kbDocument.setUpdateTime(LocalDateTime.now());

            kbDocumentMapper.insert(kbDocument);

            log.info("文档记录已保存到数据库: documentId={}, fileName={}", documentId, originalFilename);

            progressService.updateProgress(documentId, "uploading", 30, "文件保存成功，开始处理");

            // 异步处理文档
            processDocumentAsync(documentId, originalFilename, relativePath, file.getSize(), fileExtension);

            return documentId;

        } catch (Exception e) {
            progressService.setError(documentId, "文件上传失败: " + e.getMessage());
            log.error("文档上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 异步处理文档
     */
    @Async
    public void processDocumentAsync(Long documentId, String fileName, String relativePath, Long fileSize, String fileType) {
        try {
            log.info("开始处理文档: documentId={}, fileName={}", documentId, fileName);

            // 阶段1：解析文件
            progressService.updateProgress(documentId, "parsing", 40, "正在解析文件内容");
            String content = readFileContent(relativePath, fileType);

            // 阶段2：文本分块
            progressService.updateProgress(documentId, "chunking", 50, "正在对文本进行分块处理");
            List<String> chunks = splitText(content);

            progressService.updateChunkProgress(documentId, 0, chunks.size());

            // 阶段3：向量化并存储
            progressService.updateProgress(documentId, "embedding", 60, "开始生成向量并存储");
            int chunkIndex = 0;
            for (String chunk : chunks) {
                try {
                    // 模拟向量化和存储（这里需要实际的嵌入模型）
                    saveChunkToQdrant(documentId, fileName, chunkIndex, chunk);

                    chunkIndex++;
                    progressService.updateChunkProgress(documentId, chunkIndex, chunks.size());

                    // 每处理几个块更新一次进度
                    if (chunkIndex % Math.max(1, chunks.size() / 10) == 0) {
                        int chunkProgress = 60 + (chunkIndex * 30) / chunks.size();
                        progressService.updateProgress(documentId, "embedding", chunkProgress,
                                String.format("处理文本块 %d/%d", chunkIndex, chunks.size()));
                    }

                } catch (Exception e) {
                    log.error("处理文本块失败: documentId={}, chunkIndex={}, error={}",
                            documentId, chunkIndex, e.getMessage());
                }
            }

            // 完成 - 更新数据库状态
            progressService.setCompleted(documentId);

            // 更新数据库记录状态为成功
            KbDocument updateDoc = new KbDocument();
            updateDoc.setId(documentId);
            updateDoc.setStatus(KbDocument.Status.SUCCESS.getCode());
            updateDoc.setUpdateTime(LocalDateTime.now());
            kbDocumentMapper.updateById(updateDoc);

            log.info("文档处理完成: documentId={}, 总块数={}", documentId, chunks.size());

        } catch (Exception e) {
            progressService.setError(documentId, "文档处理失败: " + e.getMessage());

            // 更新数据库记录状态为失败
            try {
                KbDocument failDoc = new KbDocument();
                failDoc.setId(documentId);
                failDoc.setStatus(KbDocument.Status.FAILED.getCode());
                failDoc.setFailMsg(e.getMessage());
                failDoc.setUpdateTime(LocalDateTime.now());
                kbDocumentMapper.updateById(failDoc);
            } catch (Exception updateError) {
                log.error("更新数据库失败状态失败: {}", updateError.getMessage());
            }

            log.error("文档处理失败: documentId={}, error={}", documentId, e.getMessage(), e);
        }
    }

    /**
     * 异步处理文档（仅ID版本）
     */
    @Async
    public void processDocumentAsync(Long documentId) {
        try {
            log.info("开始重新处理文档: documentId={}", documentId);

            // 从数据库获取文档信息
            KbDocument document = kbDocumentMapper.selectById(documentId);
            if (document == null) {
                log.error("文档不存在: documentId={}", documentId);
                progressService.setError(documentId, "文档不存在");
                return;
            }

            progressService.updateProgress(documentId, "parsing", 40, "开始重新处理文档");

            // 解析文件内容
            progressService.updateProgress(documentId, "parsing", 50, "正在解析文件内容");
            String content = readFileContent(document.getFileRelativePath(), document.getFileType());

            // 文本分块
            progressService.updateProgress(documentId, "chunking", 60, "正在对文本进行分块处理");
            List<String> chunks = splitText(content);

            progressService.updateChunkProgress(documentId, 0, chunks.size());

            // 向量化并存储
            progressService.updateProgress(documentId, "embedding", 70, "开始生成向量并存储");
            int chunkIndex = 0;
            for (String chunk : chunks) {
                try {
                    saveChunkToQdrant(documentId, document.getFileName(), chunkIndex, chunk);

                    chunkIndex++;
                    progressService.updateChunkProgress(documentId, chunkIndex, chunks.size());

                    // 每处理几个块更新一次进度
                    if (chunkIndex % Math.max(1, chunks.size() / 10) == 0) {
                        int chunkProgress = 70 + (chunkIndex * 20) / chunks.size();
                        progressService.updateProgress(documentId, "embedding", chunkProgress,
                                String.format("处理文本块 %d/%d", chunkIndex, chunks.size()));
                    }

                } catch (Exception e) {
                    log.error("处理文本块失败: documentId={}, chunkIndex={}, error={}",
                            documentId, chunkIndex, e.getMessage());
                }
            }

            // 完成 - 更新数据库状态
            progressService.setCompleted(documentId);

            // 更新数据库记录状态为成功
            KbDocument updateDoc = new KbDocument();
            updateDoc.setId(documentId);
            updateDoc.setStatus(KbDocument.Status.SUCCESS.getCode());
            updateDoc.setUpdateTime(LocalDateTime.now());
            kbDocumentMapper.updateById(updateDoc);

            log.info("文档重新处理完成: documentId={}, 总块数={}", documentId, chunks.size());

        } catch (Exception e) {
            progressService.setError(documentId, "文档重新处理失败: " + e.getMessage());

            // 更新数据库记录状态为失败
            try {
                KbDocument failDoc = new KbDocument();
                failDoc.setId(documentId);
                failDoc.setStatus(KbDocument.Status.FAILED.getCode());
                failDoc.setFailMsg(e.getMessage());
                failDoc.setUpdateTime(LocalDateTime.now());
                kbDocumentMapper.updateById(failDoc);
            } catch (Exception updateError) {
                log.error("更新数据库失败状态失败: {}", updateError.getMessage());
            }

            log.error("文档重新处理失败: documentId={}, error={}", documentId, e.getMessage(), e);
        }
    }

    /**
     * 保存文件到磁盘
     */
    private String saveFile(MultipartFile file, Long documentId, String fileExtension) throws IOException {
        // 获取项目根目录的绝对路径
        String projectRoot = System.getProperty("user.dir");

        // 创建上传目录
        String dateDir = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uploadDir = projectRoot + File.separator + "kb_upload" + File.separator + dateDir;

        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 生成文件名
        String fileName = documentId + "_" + System.currentTimeMillis() + "." + fileExtension;
        File targetFile = new File(dir, fileName);

        // 保存文件
        file.transferTo(targetFile);

        // 返回相对路径用于存储
        return "kb_upload" + File.separator + dateDir + File.separator + fileName;
    }

    /**
     * 读取文件内容（增强错误处理）
     */
    private String readFileContent(String relativePath, String fileType) throws IOException {
        // 将相对路径转换为绝对路径
        String projectRoot = System.getProperty("user.dir");
        String absolutePath = projectRoot + File.separator + relativePath.replace("/", File.separator);

        File file = new File(absolutePath);
        if (!file.exists()) {
            throw new IOException("文件不存在: " + absolutePath);
        }

        try {
            return switch (fileType.toLowerCase()) {
                case "txt", "md" -> readTextFile(file);
                case "pdf" -> readPdfContent(file);
                case "docx" -> readDocxContent(file);
                default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            };
        } catch (IOException e) {
            // 提供更友好的错误信息
            if (e.getMessage().contains("字体") || e.getMessage().contains("font")) {
                throw new IOException("PDF文件字体格式不支持，建议重新保存PDF或转换为其他格式");
            } else if (e.getMessage().contains("加密") || e.getMessage().contains("password")) {
                throw new IOException("PDF文件已加密，请提供密码或使用未加密的PDF");
            } else if (e.getMessage().contains("损坏") || e.getMessage().contains("corrupted")) {
                throw new IOException("文件已损坏，请重新上传");
            } else {
                throw e; // 重新抛出原始异常
            }
        }
    }

    /**
     * 读取文本文件（添加编码检测）
     */
    private String readTextFile(File file) throws IOException {
        try {
            // 尝试UTF-8编码
            return new String(Files.readAllBytes(file.toPath()), "UTF-8");
        } catch (Exception e) {
            // 如果UTF-8失败，尝试系统默认编码
            log.warn("UTF-8编码读取失败，尝试系统默认编码: {}", e.getMessage());
            return new String(Files.readAllBytes(file.toPath()));
        }
    }

    /**
     * 读取PDF内容（增强鲁棒性）
     */
    private String readPdfContent(File file) throws IOException {
        org.apache.pdfbox.pdmodel.PDDocument document = null;
        try {
            // 尝试加载PDF文件
            document = org.apache.pdfbox.pdmodel.PDDocument.load(file);

            // 配置PDFTextStripper以更好地处理复杂PDF
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true); // 按位置排序，提高文本提取准确性
            stripper.setDropThreshold(1.0f); // 设置字符删除阈值

            String content = stripper.getText(document);
            document.close();

            if (content == null || content.trim().isEmpty()) {
                throw new IOException("PDF文件没有可提取的文本内容，可能是扫描版PDF或图片PDF");
            }

            return content.trim();

        } catch (Exception e) {
            if (document != null) {
                try {
                    document.close();
                } catch (Exception closeException) {
                    log.error("关闭PDF文档失败: {}", closeException.getMessage());
                }
            }

            if (e.getMessage() != null && e.getMessage().contains("Missing descendant font dictionary")) {
                throw new IOException("PDF文件使用了不支持的字体格式，建议转换为标准PDF或使用其他格式");
            } else if (e.getMessage() != null && (e.getMessage().contains("password") || e.getMessage().contains("encrypted"))) {
                throw new IOException("PDF文件已加密，请提供密码或使用未加密的PDF");
            } else {
                throw new IOException("PDF解析失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
            }
        }
    }

    /**
     * 读取DOCX内容
     */
    private String readDocxContent(File file) throws IOException {
        org.apache.poi.xwpf.usermodel.XWPFDocument document =
                new org.apache.poi.xwpf.usermodel.XWPFDocument(Files.newInputStream(file.toPath()));

        StringBuilder content = new StringBuilder();
        for (org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph : document.getParagraphs()) {
            content.append(paragraph.getText()).append("\n");
        }

        document.close();
        return content.toString().trim();
    }

    /**
     * 文本分块（优化内存使用）
     */
    private List<String> splitText(String content) {
        List<String> chunks = new ArrayList<>();

        // 根据内容长度动态调整分块大小
        int contentLength = content.length();
        int chunkSize;
        int overlap;
        int maxChunks;

        if (contentLength <= 10000) {
            // 小文件：细粒度分块
            chunkSize = 500;
            overlap = 100;
            maxChunks = 200;
        } else if (contentLength <= 100000) {
            // 中等文件：中等分块
            chunkSize = 1000;
            overlap = 200;
            maxChunks = 500;
        } else {
            // 大文件：粗粒度分块，防止内存溢出
            chunkSize = 2000;
            overlap = 300;
            maxChunks = 1000;
        }

        log.info("文本分块配置: 内容长度={}, 块大小={}, 重叠={}, 最大块数={}",
                contentLength, chunkSize, overlap, maxChunks);

        if (contentLength <= chunkSize) {
            chunks.add(content);
            return chunks;
        }

        int start = 0;
        int chunkCount = 0;

        while (start < contentLength && chunkCount < maxChunks) {
            int end = Math.min(start + chunkSize, contentLength);

            // 尝试在句子边界处分割
            if (end < contentLength) {
                // 限制搜索范围，避免对大字符串进行全量搜索
                int searchStart = Math.max(start, end - 200);
                String searchRange = content.substring(searchStart, end);

                int lastPeriod = searchRange.lastIndexOf(".");
                int lastQuestion = searchRange.lastIndexOf("?");
                int lastExclamation = searchRange.lastIndexOf("!");
                int lastBoundary = Math.max(Math.max(lastPeriod, lastQuestion), lastExclamation);

                if (lastBoundary >= 0) {
                    end = searchStart + lastBoundary + 1;
                }
            }

            String chunk = content.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                chunkCount++;
            }

            start = end - overlap;

            // 防止无限循环
            if (start >= end - overlap) {
                start = end;
            }
        }

        log.info("文本分块完成: 实际块数={}, 内容使用率={}%",
                chunkCount, (start * 100.0 / contentLength));

        return chunks;
    }

    /**
     * 保存文本块到Qdrant（修复版本）
     */
    private void saveChunkToQdrant(Long documentId, String fileName, int chunkIndex, String chunk) {
        try {
            // 1. 生成嵌入向量
            float[] embeddingVector = generateEmbedding(chunk);

            // 验证向量维度
            if (embeddingVector.length != qdrantConfig.getVectorSize()) {
                log.error("向量维度错误: 期望={}, 实际={}", qdrantConfig.getVectorSize(), embeddingVector.length);
                return; // 跳过这个块的存储
            }

            // 2. 构建Qdrant点数据 - 使用正确的API格式
            ObjectNode pointData = objectMapper.createObjectNode();
            pointData.put("id", documentId * 10000 + chunkIndex); // 唯一ID

            // 构建向量
            ArrayNode vectorNode = pointData.putArray("vector");
            for (float v : embeddingVector) {
                vectorNode.add(v);
            }

            // 构建payload
            ObjectNode payload = pointData.putObject("payload");
            payload.put("documentId", documentId);
            payload.put("fileName", fileName);
            payload.put("chunkIndex", chunkIndex);
            payload.put("text", chunk);
            payload.put("timestamp", System.currentTimeMillis());

            // 3. 发送到Qdrant - 修复API格式
            // Qdrant v1.x 使用 PUT 方法，points 数组格式
            ObjectNode insertRequest = objectMapper.createObjectNode();
            insertRequest.put("wait", true); // 等待操作完成
            ArrayNode pointsArray = insertRequest.putArray("points");
            pointsArray.add(pointData);

            // 详细调试信息
            log.debug("准备存储到Qdrant: documentId={}, chunkIndex={}, vectorSize={}, textLength={}",
                    documentId, chunkIndex, embeddingVector.length, chunk.length());
            log.debug("Qdrant请求URL: {}", qdrantConfig.getPointsUrl());
            log.debug("请求JSON: {}", insertRequest.toString());

            Request httpRequest = new Request.Builder()
                    .url(qdrantConfig.getPointsUrl())
                    .put(RequestBody.create(insertRequest.toString(), MediaType.parse("application/json"))) // 修复：使用PUT方法
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.isSuccessful()) {
                    log.info("成功存储文本块到Qdrant: documentId={}, chunkIndex={}, textLength={}",
                            documentId, chunkIndex, chunk.length());
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("========================================");
                    log.error("Qdrant存储失败: code={}, documentId={}, chunkIndex={}",
                            response.code(), documentId, chunkIndex);
                    log.error("错误详情: {}", errorBody);
                    log.error("向量维度: {}, Qdrant期望: {}", embeddingVector.length, qdrantConfig.getVectorSize());
                    log.error("请求URL: {}", qdrantConfig.getPointsUrl());
                    log.error("========================================");
                }
            }

        } catch (Exception e) {
            log.error("存储文本块失败: documentId={}, chunkIndex={}, error={}",
                    documentId, chunkIndex, e.getMessage(), e);
        }
    }

    /**
     * 生成嵌入向量（优先使用本地BGE服务）
     */
    private float[] generateEmbedding(String text) {
        try {
            // 优先级1: 使用本地BGE嵌入服务
            if (bgeEmbeddingService.isAvailable()) {
                log.debug("使用本地BGE服务生成嵌入向量");
                float[] bgeVector = bgeEmbeddingService.generateEmbedding(text);
                if (bgeVector != null && bgeVector.length > 0) {
                    log.info("成功使用BGE本地服务生成向量，维度: {}", bgeVector.length);
                    return bgeVector;
                }
            }

            // 优先级2: 尝试使用API密钥服务
            if (embeddingConfig.hasValidConfig()) {
                log.debug("尝试使用API密钥服务生成嵌入向量");
                float[] apiVector = generateEmbeddingFromAPI(text);
                if (apiVector != null && apiVector.length > 0) {
                    log.info("成功使用API服务生成向量，维度: {}", apiVector.length);
                    return apiVector;
                }
            }

            // 优先级3: 使用文本特征向量（备选方案）
            log.info("使用文本特征向量（备选方案）");
            return generateTextFeatureVector(text);

        } catch (Exception e) {
            log.warn("嵌入向量生成失败: {}, 使用文本特征向量", e.getMessage());
            return generateTextFeatureVector(text);
        }
    }

    /**
     * 使用API密钥服务生成嵌入向量
     */
    private float[] generateEmbeddingFromAPI(String text) {
        try {
            // 构造API请求
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", embeddingConfig.getModel());
            requestBody.put("input", text);

            Request httpRequest = new Request.Builder()
                    .url(embeddingConfig.getEmbeddingUrl())
                    .addHeader("Authorization", "Bearer " + embeddingConfig.getApiKey())
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode jsonResponse = objectMapper.readTree(responseBody);

                    // 尝试多种可能的响应格式
                    JsonNode embedding = jsonResponse.path("data").path("embedding");
                    if (embedding.isMissingNode() || !embedding.isArray()) {
                        embedding = jsonResponse.path("embedding");
                    }

                    if (embedding.isArray() && embedding.size() > 0) {
                        float[] vector = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) {
                            vector[i] = (float) embedding.get(i).asDouble();
                        }
                        log.info("成功生成API嵌入向量，维度: {}, Qdrant期望维度: {}",
                                vector.length, qdrantConfig.getVectorSize());

                        // 检查向量维度是否匹配
                        if (vector.length != qdrantConfig.getVectorSize()) {
                            log.warn("向量维度不匹配! API返回: {}, Qdrant期望: {}, 需要调整Qdrant配置",
                                    vector.length, qdrantConfig.getVectorSize());
                        }

                        return vector;
                    } else {
                        log.warn("API响应格式不正确: {}", responseBody);
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("API嵌入服务调用失败: code={}, error={}", response.code(), errorBody);
                }
            }

        } catch (Exception e) {
            log.error("API密钥嵌入服务调用异常: {}", e.getMessage());
        }

        // 失败时使用备选方案
        log.info("API嵌入服务不可用，使用文本特征向量");
        return generateTextFeatureVector(text);
    }

    /**
     * 生成文本特征向量（备选方案）
     * 基于字符统计、TF等特征生成简单的向量表示
     */
    private float[] generateTextFeatureVector(String text) {
        int vectorSize = qdrantConfig.getVectorSize();
        float[] vector = new float[vectorSize];

        // 简单的文本特征提取
        if (text != null && !text.isEmpty()) {
            // 使用文本的哈希特征
            for (int i = 0; i < text.length() && i < 1000; i++) {
                char c = text.charAt(i);
                int hash = (c * 31 + i * 17) % vectorSize;
                vector[hash] += 0.1f; // 简单的特征权重
            }

            // 归一化
            float norm = 0;
            for (float v : vector) {
                norm += v * v;
            }
            norm = (float) Math.sqrt(norm);

            if (norm > 0) {
                for (int i = 0; i < vectorSize; i++) {
                    vector[i] /= norm;
                }
            }
        }

        log.debug("生成文本特征向量，维度: {}", vectorSize);
        return vector;
    }

    /**
     * 知识库检索（真实版本）
     */
    public List<KbSearchResult> search(KbSearchRequest request) {
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }

        try {
            log.info("=== 开始知识库检索 ===");
            log.info("检索查询: {}", request.getQuery());
            log.info("检索参数: topN={}, minScore={}", request.getTopN(), request.getMinScore());

            // 1. 为查询生成嵌入向量
            float[] queryVector = generateEmbedding(request.getQuery());
            log.info("查询向量生成完成，维度: {}", queryVector.length);

            // 2. 构建Qdrant搜索请求
            ObjectNode searchRequest = objectMapper.createObjectNode();
            int topN = request.getTopN() > 0 ? request.getTopN() : 10;
            double minScore = request.getMinScore() > 0 ? request.getMinScore() : 0.3; // 降低默认阈值到0.3

            searchRequest.put("limit", topN);
            searchRequest.put("score_threshold", minScore);

            log.info("搜索参数: limit={}, score_threshold={}", topN, minScore);

            // 添加查询向量
            ArrayNode vectorNode = searchRequest.putArray("vector");
            for (float v : queryVector) {
                vectorNode.add(v);
            }

            // 添加过滤条件（如果指定了文档ID）
            if (request.getDocumentIds() != null && request.getDocumentIds().length > 0) {
                ObjectNode filter = searchRequest.putObject("filter");
                ObjectNode must = filter.putObject("must");
                ObjectNode keyCondition = must.putObject("key");
                keyCondition.put("key", "documentId");

                ArrayNode match = must.putArray("match");
                ObjectNode matchAny = match.addObject();
                ArrayNode values = matchAny.putArray("any");
                for (Long docId : request.getDocumentIds()) {
                    values.add(docId);
                }
            }

            // 3. 发送搜索请求到Qdrant
            log.info("发送搜索请求到Qdrant: {}", qdrantConfig.getSearchUrl());
            Request httpRequest = new Request.Builder()
                    .url(qdrantConfig.getSearchUrl())
                    .post(RequestBody.create(searchRequest.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                log.info("Qdrant响应状态: {}", response.code());

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("Qdrant搜索失败: code={}, error={}", response.code(), errorBody);
                    return Collections.emptyList();
                }

                // 4. 解析搜索结果
                if (response.body() != null) {
                    String responseBody = response.body().string();
                    log.debug("Qdrant原始响应: {}", responseBody);

                    JsonNode jsonResponse = objectMapper.readTree(responseBody);
                    List<KbSearchResult> results = parseSearchResults(jsonResponse);

                    log.info("=== 检索结果统计 ===");
                    log.info("找到结果数量: {}", results.size());
                    if (!results.isEmpty()) {
                        for (int i = 0; i < Math.min(3, results.size()); i++) {
                            KbSearchResult result = results.get(i);
                            log.info("结果{}: 文档ID={}, 文件名={}, 相似度={}, 文本预览={}",
                                    i + 1, result.getDocumentId(), result.getFileName(),
                                    String.format("%.3f", result.getScore()),
                                    result.getText() != null ?
                                        result.getText().substring(0, Math.min(50, result.getText().length())) + "..." : "空");
                        }
                    } else {
                        log.warn("未找到任何匹配结果，可能原因:");
                        log.warn("1. Qdrant中没有存储任何向量数据");
                        log.warn("2. 相似度阈值设置过高");
                        log.warn("3. 查询向量与存储向量差异过大");
                        log.warn("4. 向量维度不匹配");
                    }

                    return results;
                }
            }

            log.warn("Qdrant响应体为空");
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("知识库检索失败: query={}, error={}", request.getQuery(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析Qdrant搜索结果
     */
    private List<KbSearchResult> parseSearchResults(JsonNode jsonResponse) {
        List<KbSearchResult> results = new ArrayList<>();

        try {
            JsonNode resultArray = jsonResponse.path("result");
            if (resultArray.isArray()) {
                for (JsonNode resultNode : resultArray) {
                    KbSearchResult result = new KbSearchResult();

                    // 提取相似度分数
                    double score = resultNode.path("score").asDouble();
                    result.setScore(score);

                    // 提取payload信息
                    JsonNode payload = resultNode.path("payload");
                    result.setDocumentId(payload.path("documentId").asLong());
                    result.setFileName(payload.path("fileName").asText());
                    result.setChunkIndex(payload.path("chunkIndex").asInt());
                    result.setText(payload.path("text").asText());

                    results.add(result);
                }
            }
        } catch (Exception e) {
            log.error("解析搜索结果失败: {}", e.getMessage());
        }

        return results;
    }

    /**
     * 获取所有文档列表
     */
    public List<KbDocument> getAllDocuments() {
        try {
            log.info("获取文档列表");
            // 从数据库查询所有文档
            return kbDocumentMapper.selectAllDocuments();
        } catch (Exception e) {
            log.error("获取文档列表失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除文档（完整实现）
     */
    public void deleteDocument(Long documentId) {
        log.info("开始删除文档: documentId={}", documentId);

        try {
            // 1. 从数据库获取文档信息
            KbDocument document = kbDocumentMapper.selectById(documentId);
            if (document == null) {
                log.warn("文档不存在: documentId={}", documentId);
                throw new IllegalArgumentException("文档不存在");
            }

            // 2. 删除Qdrant中的向量数据
            deleteVectorsFromQdrant(documentId);

            // 3. 删除磁盘文件
            deleteDiskFile(document.getFileRelativePath());

            // 4. 删除数据库记录
            kbDocumentMapper.deleteById(documentId);

            // 5. 删除进度记录
            progressService.removeProgress(documentId);

            log.info("文档删除成功: documentId={}, fileName={}", documentId, document.getFileName());

        } catch (Exception e) {
            log.error("删除文档失败: documentId={}, error={}", documentId, e.getMessage(), e);
            throw new RuntimeException("删除文档失败: " + e.getMessage());
        }
    }

    /**
     * 从Qdrant删除向量数据
     */
    private void deleteVectorsFromQdrant(Long documentId) {
        try {
            // 构建删除请求：删除所有属于该文档的向量点
            ObjectNode deleteRequest = objectMapper.createObjectNode();
            ObjectNode filter = deleteRequest.putObject("filter");

            // 创建must条件
            ObjectNode must = filter.putObject("must");
            ObjectNode keyCondition = must.putObject("key");
            keyCondition.put("key", "documentId");

            // 创建match条件
            ObjectNode match = must.putObject("match");
            match.put("value", documentId);

            Request httpRequest = new Request.Builder()
                    .url(qdrantConfig.getCollectionUrl() + "/points/delete")
                    .post(RequestBody.create(deleteRequest.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (response.isSuccessful()) {
                    log.debug("成功从Qdrant删除文档向量: documentId={}", documentId);
                } else {
                    log.warn("Qdrant删除失败: {}, documentId={}", response.code(), documentId);
                }
            }

        } catch (Exception e) {
            log.error("从Qdrant删除向量失败: documentId={}, error={}", documentId, e.getMessage());
            // 继续执行，不因向量删除失败而阻止整个删除操作
        }
    }

    /**
     * 删除磁盘文件
     */
    private void deleteDiskFile(String relativePath) {
        try {
            String projectRoot = System.getProperty("user.dir");
            String absolutePath = projectRoot + File.separator + relativePath.replace("/", File.separator);

            File file = new File(absolutePath);
            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    log.debug("成功删除磁盘文件: {}", absolutePath);
                } else {
                    log.warn("删除磁盘文件失败: {}", absolutePath);
                }
            } else {
                log.debug("磁盘文件不存在或已被删除: {}", absolutePath);
            }

        } catch (Exception e) {
            log.error("删除磁盘文件失败: relativePath={}, error={}", relativePath, e.getMessage());
            // 继续执行，不因文件删除失败而阻止整个删除操作
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1) : "";
    }

    /**
     * 初始化Qdrant集合
     */
    public void initializeCollection() {
        try {
            log.info("初始化Qdrant集合");

            ObjectNode createRequest = objectMapper.createObjectNode();
            ObjectNode vectors = createRequest.putObject("vectors");
            vectors.put("size", qdrantConfig.getVectorSize());
            vectors.put("distance", "Cosine");

            Request createCollectionRequest = new Request.Builder()
                    .url(qdrantConfig.getCollectionUrl())
                    .put(RequestBody.create(createRequest.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response createResponse = httpClient.newCall(createCollectionRequest).execute()) {
                if (createResponse.isSuccessful()) {
                    log.info("Qdrant集合创建成功");
                } else {
                    log.warn("Qdrant集合可能已存在或创建失败: {}", createResponse.code());
                }
            }

        } catch (Exception e) {
            log.error("初始化Qdrant集合失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查Qdrant集合状态和数据数量
     */
    public Map<String, Object> checkQdrantStatus() {
        Map<String, Object> status = new HashMap<>();
        try {
            log.info("检查Qdrant集合状态...");

            // 获取集合信息
            Request getInfoRequest = new Request.Builder()
                    .url(qdrantConfig.getCollectionUrl())
                    .get()
                    .build();

            try (Response response = httpClient.newCall(getInfoRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode jsonResponse = objectMapper.readTree(responseBody);

                    JsonNode result = jsonResponse.path("result");
                    status.put("collection_exists", true);
                    status.put("vector_count", result.path("points_count").asLong());
                    status.put("vector_size", result.path("config").path("params").path("vectors").path("size").asInt());
                    status.put("distance_metric", result.path("config").path("params").path("vectors").path("distance").asText());

                    log.info("Qdrant集合状态: {}", status);
                } else {
                    status.put("collection_exists", false);
                    status.put("error", "集合不存在或无法访问");
                    log.warn("Qdrant集合不存在或无法访问: {}", response.code());
                }
            }

        } catch (Exception e) {
            status.put("error", e.getMessage());
            log.error("检查Qdrant状态失败: {}", e.getMessage(), e);
        }

        return status;
    }
}