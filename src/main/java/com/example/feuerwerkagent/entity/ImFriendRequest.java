package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友申请
 */
@Data
@TableName("im_friend_request")
public class ImFriendRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    /**
     * 验证消息
     */
    private String message;

    /**
     * 0待处理 1已同意 2已拒绝
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime handleTime;

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;
}
