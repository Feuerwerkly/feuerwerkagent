package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品（含价格、库存、图片、简介、详情）
 */
@Data
@TableName("shop_product")
public class ShopProduct {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品分类
     */
    private String category;

    /**
     * 商品简介（列表页/详情页一句话卖点）
     */
    private String brief;

    /**
     * 商品详情（多行文本，详情页展示）
     */
    private String detail;

    /**
     * 价格（元）
     */
    private BigDecimal price;

    /**
     * 原价（划线价，用于促销对比）
     */
    private BigDecimal originalPrice;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 主图URL
     */
    private String mainImage;

    /**
     * 图集URL，逗号分隔
     */
    private String images;

    /**
     * 销量
     */
    private Integer sales;

    /**
     * 平均评分（1-5）
     */
    private BigDecimal ratingAvg;

    /**
     * 评价数量
     */
    private Integer ratingCount;

    /**
     * 状态：1上架 0下架
     */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 获取图集列表
     */
    public String[] imageList() {
        if (images == null || images.isBlank()) {
            return new String[0];
        }
        return images.split(",");
    }
}
