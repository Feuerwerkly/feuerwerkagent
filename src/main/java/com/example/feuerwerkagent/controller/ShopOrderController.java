package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.dto.ShopOrderCreateRequest;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopCartService;
import com.example.feuerwerkagent.service.ShopOrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单接口（需登录）：下单/我的订单/取消/确认收货
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/orders")
@CrossOrigin(origins = "*")
public class ShopOrderController {

    @Autowired
    private ShopOrderService orderService;

    @Autowired
    private ShopCartService cartService;

    /**
     * 创建订单（购物车结算或立即购买）
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(HttpServletRequest request,
                                                   @RequestBody ShopOrderCreateRequest body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            Map<String, Object> order = orderService.create(user.getId(), body);
            return ApiResponse.success("下单成功", order);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return ApiResponse.error("创建订单失败：" + e.getMessage());
        }
    }

    /**
     * 我的订单列表
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> myOrders(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(orderService.myOrders(user.getId()));
        } catch (Exception e) {
            log.error("订单列表查询失败", e);
            return ApiResponse.error("订单列表查询失败：" + e.getMessage());
        }
    }

    /**
     * 订单详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(orderService.myOrderDetail(user.getId(), id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("订单查询失败：" + e.getMessage());
        }
    }

    /**
     * 取消订单
     */
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            orderService.cancel(user.getId(), id);
            return ApiResponse.success("订单已取消，库存已回滚", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("取消订单失败", e);
            return ApiResponse.error("取消订单失败：" + e.getMessage());
        }
    }

    /**
     * 确认收货
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<Void> confirm(HttpServletRequest request, @PathVariable Long id) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            orderService.confirm(user.getId(), id);
            return ApiResponse.success("确认收货成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("确认收货失败：" + e.getMessage());
        }
    }
}
