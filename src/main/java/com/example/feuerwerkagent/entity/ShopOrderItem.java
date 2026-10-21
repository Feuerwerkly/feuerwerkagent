package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细（商品快照）
 */
@Data
@TableName("shop_order_item")
public class ShopOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /**
     * 商品名称快照
     */
    private String productName;

    /**
     * 商品主图快照
     */
    private String productImage;

    /**
     * 成交单价快照
     */
    private BigDecimal price;

    /**
     * 购买数量
     */
    private Integer quantity;

    /**
     * 小计金额
     */
    private BigDecimal subtotal;
}
