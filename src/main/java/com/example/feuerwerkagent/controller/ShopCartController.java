package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopCartService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 购物车接口（需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/cart")
@CrossOrigin(origins = "*")
public class ShopCartController {

    @Autowired
    private ShopCartService cartService;

    /**
     * 购物车列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(cartService.list(user.getId()));
        } catch (Exception e) {
            log.error("购物车查询失败", e);
            return ApiResponse.error("购物车查询失败：" + e.getMessage());
        }
    }

    /**
     * 购物车数量角标
     */
    @GetMapping("/count")
    public ApiResponse<Long> count(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(cartService.count(user.getId()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 加入购物车
     */
    @PostMapping
    public ApiResponse<Void> add(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            int quantity = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 1)));
            cartService.add(user.getId(), productId, quantity);
            return ApiResponse.success("已加入购物车", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("加入购物车失败", e);
            return ApiResponse.error("加入购物车失败：" + e.getMessage());
        }
    }

    /**
     * 更新数量/勾选
     */
    @PutMapping("/{cartItemId}")
    public ApiResponse<Void> update(HttpServletRequest request, @PathVariable Long cartItemId,
                                    @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            Integer quantity = body.get("quantity") == null ? null
                    : Integer.parseInt(String.valueOf(body.get("quantity")));
            Integer checked = body.get("checked") == null ? null
                    : Integer.parseInt(String.valueOf(body.get("checked")));
            cartService.update(user.getId(), cartItemId, quantity, checked);
            return ApiResponse.success("已更新", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("购物车更新失败", e);
            return ApiResponse.error("购物车更新失败：" + e.getMessage());
        }
    }

    /**
     * 移除购物车项
     */
    @DeleteMapping("/{cartItemId}")
    public ApiResponse<Void> remove(HttpServletRequest request, @PathVariable Long cartItemId) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            cartService.remove(user.getId(), cartItemId);
            return ApiResponse.success("已移除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("移除失败：" + e.getMessage());
        }
    }
}
