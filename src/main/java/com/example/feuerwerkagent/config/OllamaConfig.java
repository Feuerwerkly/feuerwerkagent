package com.example.feuerwerkagent.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "ollama.enabled", havingValue = "true", matchIfMissing = false)
public class OllamaConfig {

    @Value("${ollama.base-url}")
    private String baseUrl;

    @Value("${ollama.embedding-model}")
    private String embeddingModel;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .build();
    }
}