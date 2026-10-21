package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 原始文件名
     */
    private String fileName;

    /**
     * 项目内相对路径，例：kb_upload/20260825/xxx-uuid.pdf
     */
    private String fileRelativePath;

    /**
     * 文件大小，字节
     */
    private Long fileSize;

    /**
     * 文件后缀：pdf/docx/txt/md
     */
    private String fileType;

    /**
     * 0-待处理；1-处理成功；2-处理失败
     */
    private Integer status;

    /**
     * 处理失败错误信息
     */
    private String failMsg;

    /**
     * 上传时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 状态枚举
     */
    public enum Status {
        PENDING(0, "待处理"),
        SUCCESS(1, "处理成功"),
        FAILED(2, "处理失败");

        private final Integer code;
        private final String desc;

        Status(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public Integer getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static Status fromCode(Integer code) {
            for (Status status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return PENDING;
        }
    }

    /**
     * 获取状态描述
     */
    public String getStatusDesc() {
        return Status.fromCode(this.status).getDesc();
    }

    /**
     * 判断是否处理成功
     */
    public boolean isSuccess() {
        return Status.SUCCESS.getCode().equals(this.status);
    }

    /**
     * 判断是否处理失败
     */
    public boolean isFailed() {
        return Status.FAILED.getCode().equals(this.status);
    }

    /**
     * 判断是否待处理
     */
    public boolean isPending() {
        return Status.PENDING.getCode().equals(this.status);
    }
}