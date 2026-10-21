package com.example.feuerwerkagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * IM在线会话管理：userId -> 在线WebSocket会话集合（支持同一账号多标签页）
 * 并提供向用户/用户集合推送事件的能力
 */
@Slf4j
@Service
public class ImPresenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * userId -> 该用户的在线会话（多端/多标签页）
     */
    private final Map<Long, Set<WebSocketSession>> onlineSessions = new ConcurrentHashMap<>();

    /**
     * 用户上线（新连接建立）。返回true表示该用户从离线变为在线
     */
    public boolean online(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = onlineSessions
                .computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        boolean first = sessions.isEmpty();
        sessions.add(session);
        return first;
    }

    /**
     * 用户某会话下线。返回true表示该用户已完全离线
     */
    public boolean offline(Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null) {
            return false;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            onlineSessions.remove(userId, sessions);
            return true;
        }
        return false;
    }

    public boolean isOnline(Long userId) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 向指定用户的所有在线会话推送JSON事件（Map自动序列化）
     */
    public void pushToUser(Long userId, Map<String, Object> event) {
        Set<WebSocketSession> sessions = onlineSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            log.error("IM事件序列化失败: {}", event, e);
            return;
        }
        for (WebSocketSession session : sessions) {
            send(session, json);
        }
    }

    /**
     * 向多个用户推送（可排除某人，如群消息不回推给发送者）
     */
    public void pushToUsers(Collection<Long> userIds, Map<String, Object> event, Long excludeUserId) {
        for (Long uid : userIds) {
            if (excludeUserId != null && excludeUserId.equals(uid)) {
                continue;
            }
            pushToUser(uid, event);
        }
    }

    private void send(WebSocketSession session, String json) {
        try {
            // WebSocketSession非线程安全，单会话串行发送
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.warn("IM推送失败，关闭该会话: {}", e.getMessage());
            try {
                session.close();
            } catch (IOException ignored) {
            }
        }
    }
}
