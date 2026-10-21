-- 知识库系统数据库初始化脚本

CREATE DATABASE IF NOT EXISTS kb_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE kb_db;

-- 文档主表
CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文档ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_relative_path VARCHAR(512) NOT NULL COMMENT '项目内相对路径',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    file_type VARCHAR(32) NOT NULL COMMENT '文件后缀: pdf/docx/txt/md',
    status TINYINT DEFAULT 0 COMMENT '0-待处理;1-处理成功;2-处理失败',
    fail_msg TEXT COMMENT '处理失败错误信息',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_create_time (create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';