package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.feuerwerkagent.entity.ShopOrder;
import com.example.feuerwerkagent.entity.ShopOrderItem;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.entity.ShopReview;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ShopOrderItemMapper;
import com.example.feuerwerkagent.mapper.ShopOrderMapper;
import com.example.feuerwerkagent.mapper.ShopProductMapper;
import com.example.feuerwerkagent.mapper.ShopReviewMapper;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品评价服务：买到才能评，一单一商品一条
 */
@Service
public class ShopReviewService {

    @Autowired
    private ShopReviewMapper reviewMapper;

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopUserMapper userMapper;

    /**
     * 某商品的评价分页（含用户昵称）
     */
    public Map<String, Object> listByProduct(Long productId, int page, int size) {
        Page<ShopReview> pager = reviewMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ShopReview>().eq("product_id", productId).orderByDesc("create_time"));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ShopReview review : pager.getRecords()) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", review.getId());
            row.put("rating", review.getRating());
            row.put("content", review.getContent());
            row.put("images", review.getImages());
            row.put("createTime", review.getCreateTime());
            ShopUser user = userMapper.selectById(review.getUserId());
            row.put("nickname", user == null ? "匿名用户" : maskNickname(user.getNickname()));
            rows.add(row);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", pager.getTotal());
        result.put("reviews", rows);
        return result;
    }

    /**
     * 提交评价：校验订单归属、订单状态、是否包含该商品、是否已评过
     */
    @Transactional(rollbackFor = Exception.class)
    public void submit(ShopUser user, Long productId, Long orderId, int rating, String content, String images) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分需为1-5星");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("评价内容不能为空");
        }
        if (content.length() > 1000) {
            throw new IllegalArgumentException("评价内容过长（最多1000字）");
        }

        ShopOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() < ShopOrder.STATUS_PAID || order.getStatus() == ShopOrder.STATUS_CANCELLED) {
            throw new IllegalArgumentException("订单支付后才能评价");
        }

        boolean contains = false;
        for (ShopOrderItem item : orderItemMapper.selectByOrderId(orderId)) {
            if (item.getProductId().equals(productId)) {
                contains = true;
                break;
            }
        }
        if (!contains) {
            throw new IllegalArgumentException("该订单中不包含此商品");
        }

        Long exist = reviewMapper.selectCount(new QueryWrapper<ShopReview>()
                .eq("order_id", orderId).eq("product_id", productId));
        if (exist != null && exist > 0) {
            throw new IllegalArgumentException("该商品已评价过");
        }

        ShopReview review = new ShopReview();
        review.setProductId(productId);
        review.setOrderId(orderId);
        review.setUserId(user.getId());
        review.setRating(rating);
        review.setContent(content.trim());
        review.setImages(images);
        review.setCreateTime(LocalDateTime.now());
        reviewMapper.insert(review);

        // 重算商品评分
        productMapper.refreshRating(productId);
    }

    /**
     * 我的评价列表
     */
    public List<Map<String, Object>> myReviews(Long userId) {
        List<ShopReview> reviews = reviewMapper.selectList(new QueryWrapper<ShopReview>()
                .eq("user_id", userId).orderByDesc("create_time"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ShopReview review : reviews) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", review.getId());
            row.put("orderId", review.getOrderId());
            row.put("productId", review.getProductId());
            row.put("rating", review.getRating());
            row.put("content", review.getContent());
            row.put("createTime", review.getCreateTime());
            ShopProduct product = productMapper.selectById(review.getProductId());
            if (product != null) {
                row.put("productName", product.getName());
                row.put("productImage", product.getMainImage());
                row.put("productId", product.getId());
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * 昵称脱敏：张** 、ali***
     */
    private String maskNickname(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return "匿名用户";
        }
        if (nickname.length() == 1) {
            return nickname + "**";
        }
        return nickname.charAt(0) + "**" + (nickname.length() > 2 ? nickname.charAt(nickname.length() - 1) : "");
    }
}
