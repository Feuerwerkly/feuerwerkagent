package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目
 */
@Data
@TableName("shop_cart_item")
public class ShopCartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 结算勾选：1选中 0未选中
     */
    private Integer checked;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
