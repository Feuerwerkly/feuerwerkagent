package com.example.feuerwerkagent.config;

import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopTokenManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * IM WebSocket握手拦截器：校验 ?token= 登录令牌（浏览器WS无法携带自定义Header）
 */
@Component
public class ImHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "IM_USER_ID";
    public static final String ATTR_USER = "IM_USER";

    @Autowired
    private ShopTokenManager tokenManager;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        ShopUser user = tokenManager.resolveUser(token);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USER_ID, user.getId());
        attributes.put(ATTR_USER, user);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                return URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
