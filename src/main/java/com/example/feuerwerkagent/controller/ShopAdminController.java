package com.example.feuerwerkagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.dto.ShopProductSaveRequest;
import com.example.feuerwerkagent.entity.ShopOrder;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopOrderService;
import com.example.feuerwerkagent.service.ShopProductService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 商店管理端接口（需登录且为ADMIN角色）
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/admin")
@CrossOrigin(origins = "*")
public class ShopAdminController {

    @Autowired
    private ShopProductService productService;

    @Autowired
    private ShopOrderService orderService;

    /**
     * 管理员校验
     */
    private ShopUser requireAdmin(HttpServletRequest request) {
        ShopUser user = ShopAuthInterceptor.currentUser(request);
        if (!user.isAdmin()) {
            throw new IllegalArgumentException("需要管理员权限");
        }
        return user;
    }

    // ==================== 商品管理 ====================

    /**
     * 商品列表（含下架）
     */
    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> products(HttpServletRequest request,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "10") int size) {
        try {
            requireAdmin(request);
            Page<ShopProduct> pager = productService.adminList(keyword, page, size);
            Map<String, Object> data = new HashMap<>();
            data.put("records", pager.getRecords());
            data.put("total", pager.getTotal());
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (Exception e) {
            log.error("管理端商品查询失败", e);
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 新增商品
     */
    @PostMapping("/products")
    public ApiResponse<ShopProduct> create(HttpServletRequest request, @RequestBody ShopProductSaveRequest body) {
        try {
            requireAdmin(request);
            return ApiResponse.success("商品已创建", productService.save(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("新增商品失败", e);
            return ApiResponse.error("新增商品失败：" + e.getMessage());
        }
    }

    /**
     * 编辑商品（价格/库存/图文等）
     */
    @PutMapping("/products/{id}")
    public ApiResponse<ShopProduct> update(HttpServletRequest request, @PathVariable Long id,
                                           @RequestBody ShopProductSaveRequest body) {
        try {
            requireAdmin(request);
            body.setId(id);
            return ApiResponse.success("商品已更新", productService.save(body));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("编辑商品失败", e);
            return ApiResponse.error("编辑商品失败：" + e.getMessage());
        }
    }

    /**
     * 上架/下架
     */
    @PutMapping("/products/{id}/status")
    public ApiResponse<Void> updateStatus(HttpServletRequest request, @PathVariable Long id,
                                          @RequestParam int status) {
        try {
            requireAdmin(request);
            productService.updateStatus(id, status);
            return ApiResponse.success(status == 1 ? "已上架" : "已下架", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("操作失败：" + e.getMessage());
        }
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/products/{id}")
    public ApiResponse<Void> delete(HttpServletRequest request, @PathVariable Long id) {
        try {
            requireAdmin(request);
            productService.delete(id);
            return ApiResponse.success("商品已删除", null);
        } catch (Exception e) {
            log.error("删除商品失败", e);
            return ApiResponse.error("删除商品失败：" + e.getMessage());
        }
    }

    // ==================== 订单管理 ====================

    /**
     * 全部订单
     */
    @GetMapping("/orders")
    public ApiResponse<Map<String, Object>> orders(HttpServletRequest request,
                                                   @RequestParam(required = false) Integer status,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size) {
        try {
            requireAdmin(request);
            Page<ShopOrder> pager = orderService.adminOrders(page, size, status);
            Map<String, Object> data = new HashMap<>();
            data.put("records", pager.getRecords());
            data.put("total", pager.getTotal());
            return ApiResponse.success(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 发货
     */
    @PostMapping("/orders/{id}/ship")
    public ApiResponse<Void> ship(HttpServletRequest request, @PathVariable Long id) {
        try {
            requireAdmin(request);
            orderService.adminShip(id);
            return ApiResponse.success("已发货", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("发货失败：" + e.getMessage());
        }
    }

    /**
     * 经营统计
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest request) {
        try {
            requireAdmin(request);
            return ApiResponse.success(orderService.adminStats());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(403, e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("统计失败：" + e.getMessage());
        }
    }
}
