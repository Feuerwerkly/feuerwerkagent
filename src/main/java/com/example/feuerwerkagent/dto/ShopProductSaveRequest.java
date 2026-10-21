package com.example.feuerwerkagent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理员新增/编辑商品请求
 */
@Data
public class ShopProductSaveRequest {

    /**
     * 编辑时必填，新增为空
     */
    private Long id;

    private String name;

    private String category;

    private String brief;

    private String detail;

    private BigDecimal price;

    /**
     * 原价（划线价），可空
     */
    private BigDecimal originalPrice;

    private Integer stock;

    /**
     * 主图URL，为空时自动生成
     */
    private String mainImage;

    /**
     * 图集，逗号分隔，为空时自动生成
     */
    private String images;

    /**
     * 1上架 0下架
     */
    private Integer status;
}
