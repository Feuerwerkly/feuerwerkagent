package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.ShopReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品评价接口
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/reviews")
@CrossOrigin(origins = "*")
public class ShopReviewController {

    @Autowired
    private ShopReviewService reviewService;

    /**
     * 某商品评价列表（无需登录，路径匹配拦截器放行规则）
     */
    @GetMapping("/product/{productId}")
    public ApiResponse<Map<String, Object>> listByProduct(@PathVariable Long productId,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size) {
        try {
            return ApiResponse.success(reviewService.listByProduct(productId, page, size));
        } catch (Exception e) {
            log.error("评价查询失败", e);
            return ApiResponse.error("评价查询失败：" + e.getMessage());
        }
    }

    /**
     * 提交评价（需登录，且必须已购买该商品）
     */
    @PostMapping
    public ApiResponse<Void> submit(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            Long productId = Long.valueOf(String.valueOf(body.get("productId")));
            Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
            int rating = Integer.parseInt(String.valueOf(body.getOrDefault("rating", 5)));
            String content = String.valueOf(body.get("content"));
            String images = body.get("images") == null ? null : String.valueOf(body.get("images"));
            reviewService.submit(user, productId, orderId, rating, content, images);
            return ApiResponse.success("评价成功", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("提交评价失败", e);
            return ApiResponse.error("提交评价失败：" + e.getMessage());
        }
    }

    /**
     * 我的评价
     */
    @GetMapping("/mine")
    public ApiResponse<List<Map<String, Object>>> mine(HttpServletRequest request) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            return ApiResponse.success(reviewService.myReviews(user.getId()));
        } catch (Exception e) {
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }
}
