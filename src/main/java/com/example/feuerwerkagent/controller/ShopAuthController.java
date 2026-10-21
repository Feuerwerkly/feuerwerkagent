package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopTokenManager;
import com.example.feuerwerkagent.service.ShopUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商店用户接口：注册/登录/注销/资料/改密
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/auth")
@CrossOrigin(origins = "*")
public class ShopAuthController {

    @Autowired
    private ShopUserService userService;

    @Autowired
    private ShopTokenManager tokenManager;

    /**
     * 注册
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        try {
            return ApiResponse.success("注册成功", userService.register(
                    body.get("username"), body.get("password"), body.get("nickname")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("注册失败", e);
            return ApiResponse.error("注册失败：" + e.getMessage());
        }
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            return ApiResponse.success("登录成功", userService.login(body.get("username"), body.get("password")));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("登录失败", e);
            return ApiResponse.error("登录失败：" + e.getMessage());
        }
    }

    /**
     * 当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        ShopUser user = ShopAuthInterceptor.currentUser(request);
        return ApiResponse.success(ShopUserService.sanitize(user));
    }

    /**
     * 更新资料
     */
    @PutMapping("/profile")
    public ApiResponse<Map<String, Object>> updateProfile(HttpServletRequest request,
                                                          @RequestBody Map<String, String> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            ShopUser updated = userService.updateProfile(user,
                    body.get("nickname"), body.get("phone"), body.get("email"));
            return ApiResponse.success("资料已更新", ShopUserService.sanitize(updated));
        } catch (Exception e) {
            log.error("更新资料失败", e);
            return ApiResponse.error("更新资料失败：" + e.getMessage());
        }
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(HttpServletRequest request, @RequestBody Map<String, String> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            userService.changePassword(user, body.get("oldPassword"), body.get("newPassword"));
            return ApiResponse.success("密码已修改，请重新登录", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("修改密码失败", e);
            return ApiResponse.error("修改密码失败：" + e.getMessage());
        }
    }

    /**
     * 注销
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        tokenManager.invalidate(request.getHeader("Authorization"));
        return ApiResponse.success("已退出登录", null);
    }
}
