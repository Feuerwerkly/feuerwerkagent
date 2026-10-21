package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 好友关系（双向两行，各自可设置备注）
 */
@Data
@TableName("im_friendship")
public class ImFriendship {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户
     */
    private Long userId;

    /**
     * 好友用户ID
     */
    private Long friendId;

    /**
     * 好友备注
     */
    private String remark;

    private LocalDateTime createTime;
}
