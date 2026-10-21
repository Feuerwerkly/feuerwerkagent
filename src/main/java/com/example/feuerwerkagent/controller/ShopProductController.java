package com.example.feuerwerkagent.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.service.ShopProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品浏览接口（无需登录）
 */
@Slf4j
@RestController
@RequestMapping("/api/shop")
@CrossOrigin(origins = "*")
public class ShopProductController {

    @Autowired
    private ShopProductService productService;

    /**
     * 商品搜索分页
     */
    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false, defaultValue = "new") String sort,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "12") int size) {
        try {
            Page<ShopProduct> pager = productService.list(keyword, category, sort, page, size);
            Map<String, Object> data = new HashMap<>();
            data.put("records", pager.getRecords());
            data.put("total", pager.getTotal());
            data.put("pages", pager.getPages());
            data.put("current", pager.getCurrent());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("商品列表查询失败", e);
            return ApiResponse.error("商品列表查询失败：" + e.getMessage());
        }
    }

    /**
     * 商品详情
     */
    @GetMapping("/products/{id}")
    public ApiResponse<ShopProduct> detail(@PathVariable Long id) {
        try {
            return ApiResponse.success(productService.detail(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("商品详情查询失败", e);
            return ApiResponse.error("商品详情查询失败：" + e.getMessage());
        }
    }

    /**
     * 分类列表
     */
    @GetMapping("/categories")
    public ApiResponse<List<String>> categories() {
        try {
            return ApiResponse.success(productService.categories());
        } catch (Exception e) {
            log.error("分类查询失败", e);
            return ApiResponse.error("分类查询失败：" + e.getMessage());
        }
    }

    /**
     * 热销推荐
     */
    @GetMapping("/products-hot")
    public ApiResponse<List<ShopProduct>> hot() {
        try {
            return ApiResponse.success(productService.hot(8));
        } catch (Exception e) {
            log.error("热销查询失败", e);
            return ApiResponse.error("热销查询失败：" + e.getMessage());
        }
    }
}
