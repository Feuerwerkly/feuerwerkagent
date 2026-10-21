package com.example.feuerwerkagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.feuerwerkagent.entity.ShopProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ShopProductMapper extends BaseMapper<ShopProduct> {

    /**
     * 原子扣减库存（下单占用），库存不足时返回0
     */
    @Update("UPDATE shop_product SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity} AND status = 1")
    int deductStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 回滚库存（取消订单）
     */
    @Update("UPDATE shop_product SET stock = stock + #{quantity} WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 支付成功后增加销量
     */
    @Update("UPDATE shop_product SET sales = sales + #{quantity} WHERE id = #{id}")
    int increaseSales(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 重算商品平均评分与评价数
     */
    @Update("UPDATE shop_product SET " +
            "rating_avg = IFNULL((SELECT ROUND(AVG(rating), 1) FROM shop_review WHERE product_id = #{id}), 5.0), " +
            "rating_count = (SELECT COUNT(*) FROM shop_review WHERE product_id = #{id}) " +
            "WHERE id = #{id}")
    int refreshRating(@Param("id") Long id);
}
