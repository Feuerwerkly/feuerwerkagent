package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ImFriendService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IM联系人接口：搜索用户 / 好友申请 / 同意拒绝 / 好友列表 / 删除好友（均需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/im/contacts")
@CrossOrigin(origins = "*")
public class ImContactController {

    @Autowired
    private ImFriendService friendService;

    /**
     * 搜索用户（用户名/昵称），返回关系状态
     */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(HttpServletRequest request,
                                                         @RequestParam String keyword) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(friendService.searchUsers(user.getId(), keyword));
        } catch (Exception e) {
            log.error("IM用户搜索失败", e);
            return ApiResponse.error("搜索失败：" + e.getMessage());
        }
    }

    /**
     * 发送好友申请
     */
    @PostMapping("/requests")
    public ApiResponse<Void> sendRequest(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            Long toUserId = Long.valueOf(String.valueOf(body.get("toUserId")));
            String message = body.get("message") == null ? null : String.valueOf(body.get("message"));
            friendService.sendRequest(user.getId(), toUserId, message);
            return ApiResponse.success("好友申请已发送", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("发送好友申请失败", e);
            return ApiResponse.error("发送失败：" + e.getMessage());
        }
    }

    /**
     * 我收到的好友申请列表
     */
    @GetMapping("/requests")
    public ApiResponse<List<Map<String, Object>>> myRequests(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(friendService.myRequests(user.getId()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 同意好友申请
     */
    @PostMapping("/requests/{id}/accept")
    public ApiResponse<Map<String, Object>> accept(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success("已添加为好友", friendService.acceptRequest(user.getId(), id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("同意好友申请失败", e);
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 拒绝好友申请
     */
    @PostMapping("/requests/{id}/reject")
    public ApiResponse<Void> reject(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            friendService.rejectRequest(user.getId(), id);
            return ApiResponse.success("已拒绝", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 好友列表（含在线状态）
     */
    @GetMapping("/friends")
    public ApiResponse<List<Map<String, Object>>> friends(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(friendService.myFriends(user.getId()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 删除好友
     */
    @DeleteMapping("/friends/{friendId}")
    public ApiResponse<Void> deleteFriend(HttpServletRequest request, @PathVariable Long friendId) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            friendService.deleteFriend(user.getId(), friendId);
            return ApiResponse.success("已删除好友", null);
        } catch (Exception e) {
            return ApiResponse.error("删除失败：" + e.getMessage());
        }
    }
}
