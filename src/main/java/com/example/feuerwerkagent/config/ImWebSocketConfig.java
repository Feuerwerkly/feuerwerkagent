package com.example.feuerwerkagent.config;

import com.example.feuerwerkagent.service.ImWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * IM WebSocket端点配置：/ws/im（握手时通过 ?token= 鉴权）
 */
@Configuration
@EnableWebSocket
public class ImWebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ImWebSocketHandler imWebSocketHandler;

    @Autowired
    private ImHandshakeInterceptor imHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imWebSocketHandler, "/ws/im")
                .addInterceptors(imHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 容器参数：允许较大的图片URL等长消息
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(512 * 1024);
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        return container;
    }
}
