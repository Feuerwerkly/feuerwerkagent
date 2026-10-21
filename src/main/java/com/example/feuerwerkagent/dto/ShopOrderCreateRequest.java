package com.example.feuerwerkagent.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单请求
 */
@Data
public class ShopOrderCreateRequest {

    /**
     * 订单商品列表
     */
    private List<Item> items;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 是否来自购物车结算（下单后移除对应购物车项）
     */
    private Boolean fromCart;

    @Data
    public static class Item {
        private Long productId;
        private Integer quantity;
    }
}
