package com.example.feuerwerkagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.feuerwerkagent.entity.ShopOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShopOrderItemMapper extends BaseMapper<ShopOrderItem> {

    /**
     * 按订单ID查询明细
     */
    @Select("SELECT * FROM shop_order_item WHERE order_id = #{orderId}")
    List<ShopOrderItem> selectByOrderId(Long orderId);
}
