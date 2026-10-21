package com.example.feuerwerkagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "bge.embedding")
public class BgeEmbeddingConfig {

    /**
     * 是否启用本地BGE嵌入模型
     */
    private boolean enabled = true;

    /**
     * BGE模型文件路径
     * 可以是绝对路径或相对于项目根目录的路径
     */
    private String modelPath = "models/bge-small-zh.onnx";

    /**
     * 向量维度
     */
    private int vectorSize = 512;

    /**
     * 最大文本长度（token数量）
     */
    private int maxLength = 512;

    /**
     * 是否使用GPU加速
     */
    private boolean useGpu = false;

    /**
     * 模型下载URL（备选）
     */
    private String modelDownloadUrl = "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/model.onnx";
}