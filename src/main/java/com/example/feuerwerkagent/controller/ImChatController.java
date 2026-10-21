package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ImChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IM消息接口：会话列表 / 历史消息 / 标记已读（均需登录；发消息走WebSocket）
 */
@Slf4j
@RestController
@RequestMapping("/api/im/chat")
@CrossOrigin(origins = "*")
public class ImChatController {

    @Autowired
    private ImChatService chatService;

    /**
     * 会话列表（单聊+群聊，含最后消息与未读数）
     */
    @GetMapping("/conversations")
    public ApiResponse<List<Map<String, Object>>> conversations(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(chatService.conversations(user.getId()));
        } catch (Exception e) {
            log.error("会话列表查询失败", e);
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 历史消息（peerType=SINGLE|GROUP & peerId，beforeId向上翻页，升序返回）
     */
    @GetMapping("/messages")
    public ApiResponse<List<Map<String, Object>>> messages(HttpServletRequest request,
                                                           @RequestParam String peerType,
                                                           @RequestParam Long peerId,
                                                           @RequestParam(required = false) Long beforeId,
                                                           @RequestParam(required = false) Integer size) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(chatService.history(user.getId(), peerType, peerId, beforeId, size));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("历史消息查询失败", e);
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 标记会话已读
     */
    @PostMapping("/read")
    public ApiResponse<Void> markRead(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            String peerType = String.valueOf(body.get("peerType"));
            Long peerId = Long.valueOf(String.valueOf(body.get("peerId")));
            chatService.markRead(user.getId(), peerType, peerId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("标记已读失败", e);
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 未读总数（导航角标用）
     */
    @GetMapping("/unread-total")
    public ApiResponse<Map<String, Object>> unreadTotal(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            long total = chatService.conversations(user.getId()).stream()
                    .mapToLong(c -> ((Number) c.getOrDefault("unread", 0)).longValue()).sum();
            Map<String, Object> data = new HashMap<>();
            data.put("total", total);
            return ApiResponse.success(data);
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }
}
