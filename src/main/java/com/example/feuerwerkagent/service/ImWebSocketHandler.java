package com.example.feuerwerkagent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * IM WebSocket消息处理器
 * 客户端上行消息格式：
 *   {type:"SEND", peerType:"SINGLE|GROUP", peerId:1, contentType:"TEXT|IMAGE", content:"...", clientId:"..."}
 *   {type:"PING"}
 */
@Slf4j
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ImChatService chatService;

    @Autowired
    private ImPresenceService presenceService;

    @Autowired
    private ImFriendService friendService;

    @Autowired
    private ImCallService callService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("IM_USER_ID");
        boolean becameOnline = presenceService.online(userId, session);
        log.info("IM连接建立: userId={}, sessionId={}, 上线={}", userId, session.getId(), becameOnline);
        // 由离线变在线 -> 向在线好友广播上线状态
        if (becameOnline) {
            broadcastPresence(userId, true);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("IM_USER_ID");
        Map<String, Object> payload;
        try {
            payload = MAPPER.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            log.warn("IM消息解析失败: {}", message.getPayload());
            return;
        }
        String type = String.valueOf(payload.get("type"));
        try {
            switch (type) {
                case "SEND" -> handleSend(userId, payload);
                case "CALL" -> callService.handleCall(userId, payload);
                case "PING" -> replyPong(session);
                default -> log.debug("忽略未知IM消息类型: {}", type);
            }
        } catch (IllegalArgumentException e) {
            // 业务校验失败：回执错误给发送方
            Map<String, Object> error = new HashMap<>();
            error.put("type", "ERROR");
            Map<String, Object> data = new HashMap<>();
            data.put("msg", e.getMessage());
            if (payload.get("clientId") != null) {
                data.put("clientId", payload.get("clientId"));
            }
            error.put("data", data);
            presenceService.pushToUser(userId, error);
        } catch (Exception e) {
            log.error("处理IM消息异常", e);
        }
    }

    /**
     * 处理发送消息：chatService 负责校验、落库、推送MESSAGE/ACK
     */
    private void handleSend(Long userId, Map<String, Object> payload) {
        String peerType = String.valueOf(payload.get("peerType"));
        Long peerId = Long.valueOf(String.valueOf(payload.get("peerId")));
        String contentType = payload.get("contentType") == null ? "TEXT" : String.valueOf(payload.get("contentType"));
        String content = String.valueOf(payload.get("content"));
        String clientId = payload.get("clientId") == null ? null : String.valueOf(payload.get("clientId"));
        chatService.send(userId, peerType, peerId, contentType, content, clientId);
    }

    private void replyPong(WebSocketSession session) throws Exception {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("IM_USER_ID");
        if (userId == null) {
            return;
        }
        boolean becameOffline = presenceService.offline(userId, session);
        log.info("IM连接关闭: userId={}, sessionId={}, 完全离线={}", userId, session.getId(), becameOffline);
        if (becameOffline) {
            broadcastPresence(userId, false);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("IM传输异常: sessionId={}, {}", session.getId(), exception.getMessage());
        try {
            session.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 向该用户的在线好友广播在线状态变更
     */
    private void broadcastPresence(Long userId, boolean online) {
        Set<Long> friendIds = new HashSet<>(friendService.friendIds(userId));
        if (friendIds.isEmpty()) {
            return;
        }
        Map<String, Object> event = new HashMap<>();
        event.put("type", "PRESENCE");
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("online", online);
        event.put("data", data);
        presenceService.pushToUsers(friendIds, event, null);
    }
}
