package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话读位（记录用户在单聊/群聊中已读到的消息ID，用于未读数计算）
 */
@Data
@TableName("im_conversation")
public class ImConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * SINGLE / GROUP
     */
    private String peerType;

    /**
     * 单聊=对方用户ID；群聊=群ID
     */
    private Long peerId;

    /**
     * 已读到的最大消息ID
     */
    private Long lastReadMessageId;

    private LocalDateTime updateTime;
}
