package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 群成员
 */
@Data
@TableName("im_group_member")
public class ImGroupMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long groupId;

    private Long userId;

    /**
     * OWNER群主 / MEMBER成员
     */
    private String role;

    private LocalDateTime joinTime;

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_MEMBER = "MEMBER";
}
