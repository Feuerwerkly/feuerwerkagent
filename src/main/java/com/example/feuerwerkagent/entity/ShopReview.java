package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价
 */
@Data
@TableName("shop_review")
public class ShopReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /**
     * 关联订单（保证"买到才能评"）
     */
    private Long orderId;

    private Long userId;

    /**
     * 评分1-5星
     */
    private Integer rating;

    /**
     * 评价内容
     */
    private String content;

    /**
     * 评价图片，逗号分隔
     */
    private String images;

    private LocalDateTime createTime;
}
