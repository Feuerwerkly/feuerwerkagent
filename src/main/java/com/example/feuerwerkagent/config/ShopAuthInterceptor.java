package com.example.feuerwerkagent.config;

import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopTokenManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 商店登录拦截器：/api/shop/** 下受保护接口需要携带 Authorization: Bearer <token>
 */
@Component
public class ShopAuthInterceptor implements HandlerInterceptor {

    /**
     * request attribute key：当前登录用户
     */
    public static final String ATTR_CURRENT_USER = "SHOP_CURRENT_USER";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ShopTokenManager tokenManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        ShopUser user = tokenManager.resolveUser(token);
        if (user == null || (user.getStatus() != null && user.getStatus() != 1)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ApiResponse.error(401, "请先登录")));
            return false;
        }

        request.setAttribute(ATTR_CURRENT_USER, user);
        return true;
    }

    /**
     * 从request中取出当前登录用户（控制器用）
     */
    public static ShopUser currentUser(HttpServletRequest request) {
        return (ShopUser) request.getAttribute(ATTR_CURRENT_USER);
    }
}
