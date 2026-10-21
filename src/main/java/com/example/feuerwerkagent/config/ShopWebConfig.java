package com.example.feuerwerkagent.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 商店Web配置：登录拦截 + 上传图片静态映射
 */
@Configuration
public class ShopWebConfig implements WebMvcConfigurer {

    @Autowired
    private ShopAuthInterceptor shopAuthInterceptor;

    /**
     * 商品/评价图片上传目录
     */
    @Value("${shop.upload-dir:shop_upload}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(shopAuthInterceptor)
                .addPathPatterns("/api/shop/**", "/api/im/**")
                .excludePathPatterns(
                        // 登录注册
                        "/api/shop/auth/login",
                        "/api/shop/auth/register",
                        // 商品浏览（列表/详情/分类/搜索）
                        "/api/shop/products",
                        "/api/shop/products/**",
                        "/api/shop/categories",
                        // 商品图片（动态SVG与上传目录）
                        "/api/shop/image/**",
                        "/shop-images/**",
                        // 商品评价浏览
                        "/api/shop/reviews/product/**",
                        // 支付宝异步通知（支付宝服务器回调，不带token）
                        "/api/shop/alipay/notify"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dir = Paths.get(uploadDir).toAbsolutePath();
        dir.toFile().mkdirs();
        registry.addResourceHandler("/shop-images/**")
                .addResourceLocations(dir.toUri().toString());
    }
}
