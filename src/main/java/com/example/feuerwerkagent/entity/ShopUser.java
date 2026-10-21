package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商店用户
 */
@Data
@TableName("shop_user")
public class ShopUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（登录账号）
     */
    private String username;

    /**
     * BCrypt加密密码
     */
    private String password;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色：USER普通用户 / ADMIN管理员
     */
    private String role;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(this.role);
    }
}
