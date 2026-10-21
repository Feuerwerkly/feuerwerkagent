package com.example.feuerwerkagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.feuerwerkagent.entity.ShopOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ShopOrderMapper extends BaseMapper<ShopOrder> {

    /**
     * 条件更新为已支付（幂等：仅待支付状态可更新），返回受影响行数
     */
    @Update("UPDATE shop_order SET status = 20, trade_no = #{tradeNo}, payment_time = #{paymentTime} " +
            "WHERE order_no = #{orderNo} AND status = 10")
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("tradeNo") String tradeNo,
                 @Param("paymentTime") LocalDateTime paymentTime);
}
