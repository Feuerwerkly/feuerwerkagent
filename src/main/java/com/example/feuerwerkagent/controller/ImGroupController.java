package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ImGroupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * IM群聊接口：建群 / 我的群 / 成员 / 邀请 / 踢人 / 退群 / 解散 / 改名（均需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/im/groups")
@CrossOrigin(origins = "*")
public class ImGroupController {

    @Autowired
    private ImGroupService groupService;

    /**
     * 创建群聊
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            String name = String.valueOf(body.get("name"));
            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) body.get("memberIds");
            List<Long> memberIds = rawIds == null ? null
                    : rawIds.stream().map(Number::longValue).toList();
            return ApiResponse.success("群聊创建成功", groupService.create(user.getId(), name, memberIds));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("创建群聊失败", e);
            return ApiResponse.error("创建失败：" + e.getMessage());
        }
    }

    /**
     * 我加入的群
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> myGroups(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(groupService.myGroups(user.getId()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 群信息
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> info(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            groupService.requireMember(user.getId(), id);
            return ApiResponse.success(groupService.groupInfo(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 群成员列表
     */
    @GetMapping("/{id}/members")
    public ApiResponse<List<Map<String, Object>>> members(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(groupService.members(user.getId(), id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 邀请成员
     */
    @PostMapping("/{id}/members")
    public ApiResponse<Void> invite(HttpServletRequest request, @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            @SuppressWarnings("unchecked")
            List<Number> rawIds = (List<Number>) body.get("userIds");
            List<Long> userIds = rawIds == null ? null
                    : rawIds.stream().map(Number::longValue).toList();
            groupService.invite(user.getId(), id, userIds);
            return ApiResponse.success("邀请成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("邀请成员失败", e);
            return ApiResponse.error("邀请失败：" + e.getMessage());
        }
    }

    /**
     * 踢出成员（群主）
     */
    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<Void> kick(HttpServletRequest request, @PathVariable Long id, @PathVariable Long userId) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            groupService.kick(user.getId(), id, userId);
            return ApiResponse.success("已移出群聊", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 退出群聊
     */
    @PostMapping("/{id}/quit")
    public ApiResponse<Void> quit(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            groupService.quit(user.getId(), id);
            return ApiResponse.success("已退出群聊", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 解散群聊（群主）
     */
    @PostMapping("/{id}/disband")
    public ApiResponse<Void> disband(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            groupService.disband(user.getId(), id);
            return ApiResponse.success("群聊已解散", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 修改群名称/公告（群主）
     */
    @PutMapping("/{id}")
    public ApiResponse<Void> update(HttpServletRequest request, @PathVariable Long id,
                                    @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
            String notice = body.get("notice") == null ? null : String.valueOf(body.get("notice"));
            groupService.update(user.getId(), id, name, notice);
            return ApiResponse.success("已更新", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("更新失败：" + e.getMessage());
        }
    }
}
