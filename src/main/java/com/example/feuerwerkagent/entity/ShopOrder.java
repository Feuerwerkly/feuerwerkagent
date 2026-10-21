package com.example.feuerwerkagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单
 */
@Data
@TableName("shop_order")
public class ShopOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单号（对应支付宝out_trade_no）
     */
    private String orderNo;

    private Long userId;

    /**
     * 订单总金额
     */
    private BigDecimal totalAmount;

    /**
     * 状态：10待支付 20已支付 30已发货 40已完成 50已取消
     */
    private Integer status;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    /**
     * 订单备注
     */
    private String remark;

    /**
     * 支付宝交易号
     */
    private String tradeNo;

    /**
     * 支付时间
     */
    private LocalDateTime paymentTime;

    /**
     * 发货时间
     */
    private LocalDateTime deliveryTime;

    /**
     * 完成/收货时间
     */
    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static final int STATUS_PENDING_PAYMENT = 10;
    public static final int STATUS_PAID = 20;
    public static final int STATUS_SHIPPED = 30;
    public static final int STATUS_COMPLETED = 40;
    public static final int STATUS_CANCELLED = 50;

    /**
     * 状态枚举
     */
    public enum Status {
        PENDING_PAYMENT(10, "待支付"),
        PAID(20, "已支付"),
        SHIPPED(30, "已发货"),
        COMPLETED(40, "已完成"),
        CANCELLED(50, "已取消");

        private final Integer code;
        private final String desc;

        Status(Integer code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public Integer getCode() {
            return code;
        }

        public String getDesc() {
            return desc;
        }

        public static String descOf(Integer code) {
            for (Status s : values()) {
                if (s.code.equals(code)) {
                    return s.desc;
                }
            }
            return "未知状态";
        }
    }

    public String getStatusDesc() {
        return Status.descOf(this.status);
    }
}
