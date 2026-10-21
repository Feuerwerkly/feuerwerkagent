package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 商店图片上传接口（需登录，拦截器默认保护）
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/upload")
@CrossOrigin(origins = "*")
public class ShopUploadController {

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Value("${shop.upload-dir:shop_upload}")
    private String uploadDir;

    /**
     * 上传图片（商品图/评价图），返回可访问URL
     */
    @PostMapping("/image")
    public ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ApiResponse.error(400, "请选择图片文件");
            }
            if (file.getSize() > MAX_SIZE) {
                return ApiResponse.error(400, "图片不能超过5MB");
            }
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
            String ext;
            if (original.endsWith(".jpg") || original.endsWith(".jpeg")) {
                ext = "jpg";
            } else if (original.endsWith(".png")) {
                ext = "png";
            } else if (original.endsWith(".gif")) {
                ext = "gif";
            } else if (original.endsWith(".webp")) {
                ext = "webp";
            } else {
                return ApiResponse.error(400, "仅支持 jpg/png/gif/webp 图片");
            }

            Path dir = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            file.transferTo(dir.resolve(filename).toFile());

            Map<String, String> data = new HashMap<>();
            data.put("url", "/shop-images/" + filename);
            log.info("图片上传成功: {}", filename);
            return ApiResponse.success("上传成功", data);
        } catch (Exception e) {
            log.error("图片上传失败", e);
            return ApiResponse.error("图片上传失败：" + e.getMessage());
        }
    }
}
