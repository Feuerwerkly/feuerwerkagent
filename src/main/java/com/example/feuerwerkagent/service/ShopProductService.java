package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.feuerwerkagent.dto.ShopProductSaveRequest;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.mapper.ShopProductMapper;
import com.example.feuerwerkagent.mapper.ShopReviewMapper;
import com.example.feuerwerkagent.entity.ShopReview;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品服务：浏览/搜索/分页 + 管理员维护（价格、库存、上下架）
 */
@Service
public class ShopProductService {

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopReviewMapper reviewMapper;

    /**
     * 商品搜索分页（仅上架商品）
     *
     * @param sort    new最新 / sales销量 / priceAsc价格升序 / priceDesc价格降序
     */
    public Page<ShopProduct> list(String keyword, String category, String sort, int page, int size) {
        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("status", 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("name", keyword).or().like("brief", keyword).or().like("category", keyword));
        }
        if (StringUtils.hasText(category) && !"全部".equals(category)) {
            wrapper.eq("category", category);
        }
        if ("sales".equals(sort)) {
            wrapper.orderByDesc("sales");
        } else if ("priceAsc".equals(sort)) {
            wrapper.orderByAsc("price");
        } else if ("priceDesc".equals(sort)) {
            wrapper.orderByDesc("price");
        } else {
            wrapper.orderByDesc("create_time");
        }
        return productMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 商品详情
     */
    public ShopProduct detail(Long id) {
        ShopProduct product = productMapper.selectById(id);
        if (product == null || product.getStatus() == null || product.getStatus() != 1) {
            throw new IllegalArgumentException("商品不存在或已下架");
        }
        return product;
    }

    /**
     * 分类列表（含商品数）
     */
    public List<String> categories() {
        return productMapper.selectList(new QueryWrapper<ShopProduct>()
                        .select("DISTINCT category").eq("status", 1).isNotNull("category"))
                .stream().map(ShopProduct::getCategory).collect(Collectors.toList());
    }

    /**
     * 推荐商品（销量TOP）
     */
    public List<ShopProduct> hot(int limit) {
        return productMapper.selectList(new QueryWrapper<ShopProduct>()
                .eq("status", 1).orderByDesc("sales").last("LIMIT " + Math.min(limit, 20)));
    }

    // ==================== 管理员操作 ====================

    /**
     * 管理员商品列表（含下架）
     */
    public Page<ShopProduct> adminList(String keyword, int page, int size) {
        QueryWrapper<ShopProduct> wrapper = new QueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like("name", keyword);
        }
        wrapper.orderByDesc("create_time");
        return productMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 新增/编辑商品
     */
    public ShopProduct save(ShopProductSaveRequest req) {
        if (!StringUtils.hasText(req.getName())) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (req.getPrice() == null || req.getPrice().doubleValue() <= 0) {
            throw new IllegalArgumentException("价格必须大于0");
        }
        if (req.getStock() == null || req.getStock() < 0) {
            throw new IllegalArgumentException("库存不能为负数");
        }

        boolean isNew = req.getId() == null;
        ShopProduct product;
        if (isNew) {
            product = new ShopProduct();
            product.setSales(0);
            product.setRatingAvg(new java.math.BigDecimal("5.0"));
            product.setRatingCount(0);
            product.setCreateTime(LocalDateTime.now());
        } else {
            product = productMapper.selectById(req.getId());
            if (product == null) {
                throw new IllegalArgumentException("商品不存在");
            }
        }

        product.setName(req.getName());
        product.setCategory(StringUtils.hasText(req.getCategory()) ? req.getCategory() : "未分类");
        product.setBrief(req.getBrief());
        product.setDetail(req.getDetail());
        product.setPrice(req.getPrice());
        product.setOriginalPrice(req.getOriginalPrice());
        product.setStock(req.getStock());
        product.setStatus(req.getStatus() == null ? 1 : req.getStatus());

        if (isNew) {
            productMapper.insert(product);
        }

        // 图片处理：编辑时未传图片则保留原图；新增未传则自动生成图集
        boolean hasMain = StringUtils.hasText(req.getMainImage());
        boolean hasImages = StringUtils.hasText(req.getImages());
        if (hasMain) {
            product.setMainImage(req.getMainImage());
            product.setImages(hasImages ? req.getImages() : req.getMainImage());
        } else if (hasImages) {
            product.setImages(req.getImages());
            product.setMainImage(req.getImages().split(",")[0]);
        } else if (isNew) {
            String base = "/api/shop/image/p" + product.getId() + "-";
            product.setMainImage(base + "1");
            product.setImages(base + "1," + base + "2," + base + "3");
        }

        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        return product;
    }

    /**
     * 上架/下架
     */
    public void updateStatus(Long id, int status) {
        ShopProduct product = productMapper.selectById(id);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        product.setStatus(status);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
    }

    /**
     * 删除商品（同时删除评价）
     */
    public void delete(Long id) {
        productMapper.deleteById(id);
        reviewMapper.delete(new QueryWrapper<ShopReview>().eq("product_id", id));
    }
}
