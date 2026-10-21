package com.example.feuerwerkagent.config;

import lombok.Data;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Data
@Configuration
public class QdrantConfig {

    @Value("${qdrant.host}")
    private String host;

    @Value("${qdrant.port}")
    private int port;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    @Value("${qdrant.vector-size}")
    private int vectorSize;

    @Bean
    public OkHttpClient qdrantHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String getQdrantUrl() {
        return String.format("http://%s:%d", host, port);
    }

    public String getCollectionsUrl() {
        return getQdrantUrl() + "/collections";
    }

    public String getCollectionUrl() {
        return getCollectionsUrl() + "/" + collectionName;
    }

    public String getPointsUrl() {
        return getCollectionUrl() + "/points";
    }

    public String getSearchUrl() {
        return getCollectionUrl() + "/points/search";
    }
}