package com.example.feuerwerkagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "anthropic")
public class AnthropicConfig {
    private String apiKey;
    private String baseUrl = "https://model-api.datagrand.com";
    private String model = "deepseek-v4-pro";
    private int maxTokens = 4096;
    private double temperature = 0.7;
}
