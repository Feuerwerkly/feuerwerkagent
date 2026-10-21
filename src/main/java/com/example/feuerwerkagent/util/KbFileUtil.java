package com.example.feuerwerkagent.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class KbFileUtil {

    @Value("${kb.upload-sub-dir}")
    private String uploadSubDir;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 获取项目根目录下kb_upload的绝对路径
     */
    public File getBaseUploadDir() {
        // 开发环境：项目根目录；jar包运行：jar所在同级目录
        String basePath = System.getProperty("user.dir");
        File dir = new File(basePath, uploadSubDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * 生成存储文件名：UUID.后缀
     */
    public String generateStorageFileName(String originalExtension) {
        return UUID.randomUUID().toString().replace("-", "") + "." + originalExtension;
    }

    /**
     * 生成相对路径：kb_upload/yyyyMMdd/UUID.后缀
     */
    public String generateRelativePath(String originalExtension) {
        String dateDir = LocalDate.now().format(DATE_FORMATTER);
        String fileName = generateStorageFileName(originalExtension);
        return uploadSubDir + File.separator + dateDir + File.separator + fileName;
    }

    /**
     * 根据相对路径获取绝对路径
     */
    public File getAbsoluteFile(String relativePath) {
        String basePath = System.getProperty("user.dir");
        File file = new File(basePath, relativePath);
        return file;
    }

    /**
     * 确保目录存在
     */
    public void ensureDirExists(File file) {
        if (file != null && !file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 获取文件扩展名
     */
    public String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 验证文件类型是否支持
     */
    public boolean isSupportedFileType(String fileExtension) {
        String[] supportedTypes = {"pdf", "docx", "txt", "md"};
        for (String type : supportedTypes) {
            if (type.equalsIgnoreCase(fileExtension)) {
                return true;
            }
        }
        return false;
    }
}