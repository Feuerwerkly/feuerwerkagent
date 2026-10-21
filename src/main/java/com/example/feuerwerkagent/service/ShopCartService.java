package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.feuerwerkagent.entity.ShopCartItem;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.mapper.ShopCartItemMapper;
import com.example.feuerwerkagent.mapper.ShopProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车服务
 */
@Service
public class ShopCartService {

    @Autowired
    private ShopCartItemMapper cartItemMapper;

    @Autowired
    private ShopProductMapper productMapper;

    /**
     * 购物车列表（联商品信息）
     */
    public List<Map<String, Object>> list(Long userId) {
        List<ShopCartItem> items = cartItemMapper.selectList(
                new QueryWrapper<ShopCartItem>().eq("user_id", userId).orderByDesc("update_time"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopCartItem item : items) {
            ShopProduct product = productMapper.selectById(item.getProductId());
            if (product == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("cartItemId", item.getId());
            row.put("productId", product.getId());
            row.put("name", product.getName());
            row.put("price", product.getPrice());
            row.put("mainImage", product.getMainImage());
            row.put("stock", product.getStock());
            row.put("status", product.getStatus());
            row.put("quantity", item.getQuantity());
            row.put("checked", item.getChecked());
            row.put("subtotal", product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            result.add(row);
        }
        return result;
    }

    /**
     * 加入购物车（同商品数量累加）
     */
    public void add(Long userId, Long productId, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("数量至少为1");
        }
        ShopProduct product = productMapper.selectById(productId);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new IllegalArgumentException("商品不存在或已下架");
        }

        ShopCartItem exist = cartItemMapper.selectOne(new QueryWrapper<ShopCartItem>()
                .eq("user_id", userId).eq("product_id", productId));
        int newQty = quantity + (exist == null ? 0 : exist.getQuantity());
        if (newQty > product.getStock()) {
            throw new IllegalArgumentException("库存不足，当前库存：" + product.getStock());
        }

        if (exist == null) {
            ShopCartItem item = new ShopCartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setQuantity(newQty);
            item.setChecked(1);
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            cartItemMapper.insert(item);
        } else {
            exist.setQuantity(newQty);
            exist.setUpdateTime(LocalDateTime.now());
            cartItemMapper.updateById(exist);
        }
    }

    /**
     * 更新数量/勾选状态
     */
    public void update(Long userId, Long cartItemId, Integer quantity, Integer checked) {
        ShopCartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("购物车项不存在");
        }
        if (quantity != null) {
            ShopProduct product = productMapper.selectById(item.getProductId());
            int stock = product == null ? 0 : product.getStock();
            if (quantity < 1 || quantity > stock) {
                throw new IllegalArgumentException("数量非法，当前库存：" + stock);
            }
            item.setQuantity(quantity);
        }
        if (checked != null) {
            item.setChecked(checked == 1 ? 1 : 0);
        }
        item.setUpdateTime(LocalDateTime.now());
        cartItemMapper.updateById(item);
    }

    /**
     * 移除单个购物车项
     */
    public void remove(Long userId, Long cartItemId) {
        ShopCartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("购物车项不存在");
        }
        cartItemMapper.deleteById(cartItemId);
    }

    /**
     * 下单后移除对应购物车商品
     */
    public void removeByProducts(Long userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        cartItemMapper.delete(new QueryWrapper<ShopCartItem>()
                .eq("user_id", userId).in("product_id", productIds));
    }

    /**
     * 购物车商品数角标
     */
    public long count(Long userId) {
        return cartItemMapper.selectCount(new QueryWrapper<ShopCartItem>().eq("user_id", userId));
    }
}
