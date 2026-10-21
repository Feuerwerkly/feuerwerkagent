package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群聊
 */
@Data
@TableName("im_group")
public class ImGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /**
     * 群主用户ID
     */
    private Long ownerId;

    /**
     * 群公告
     */
    private String notice;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
