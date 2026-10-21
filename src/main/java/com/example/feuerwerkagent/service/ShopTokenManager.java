package com.example.feuerwerkagent.service;

import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商店登录令牌管理（内存版，重启后需重新登录）
 */
@Component
public class ShopTokenManager {

    /**
     * token -> userId
     */
    private final Map<String, Long> tokenUserMap = new ConcurrentHashMap<>();

    /**
     * userId -> token（一个用户同时只保留一个有效token）
     */
    private final Map<Long, String> userTokenMap = new ConcurrentHashMap<>();

    @Autowired
    private ShopUserMapper userMapper;

    /**
     * 登录成功后签发token
     */
    public String issueToken(Long userId) {
        // 旧token失效
        String old = userTokenMap.remove(userId);
        if (old != null) {
            tokenUserMap.remove(old);
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenUserMap.put(token, userId);
        userTokenMap.put(userId, token);
        return token;
    }

    /**
     * 根据token解析当前用户，无效返回null
     */
    public ShopUser resolveUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId = tokenUserMap.get(token);
        if (userId == null) {
            return null;
        }
        return userMapper.selectById(userId);
    }

    /**
     * 注销
     */
    public void invalidate(String token) {
        if (token == null) {
            return;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long userId = tokenUserMap.remove(token);
        if (userId != null) {
            userTokenMap.remove(userId);
        }
    }
}
