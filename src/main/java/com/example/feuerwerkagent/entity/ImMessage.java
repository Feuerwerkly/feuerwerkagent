package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息：单聊存 toUserId，群聊存 groupId
 */
@Data
@TableName("im_message")
public class ImMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * SINGLE单聊 / GROUP群聊
     */
    private String peerType;

    private Long fromUserId;

    /**
     * 单聊：接收方用户ID
     */
    private Long toUserId;

    /**
     * 群聊：群ID
     */
    private Long groupId;

    /**
     * TEXT文本 / IMAGE图片URL
     */
    private String contentType;

    private String content;

    private LocalDateTime createTime;

    public static final String TYPE_SINGLE = "SINGLE";
    public static final String TYPE_GROUP = "GROUP";
    public static final String CONTENT_TEXT = "TEXT";
    public static final String CONTENT_IMAGE = "IMAGE";
}
