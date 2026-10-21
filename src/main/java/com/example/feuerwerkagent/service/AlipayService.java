package com.example.feuerwerkagent.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.example.feuerwerkagent.config.AlipayConfigProperties;
import com.example.feuerwerkagent.entity.ShopOrder;
import com.example.feuerwerkagent.entity.ShopOrderItem;
import com.example.feuerwerkagent.mapper.ShopOrderItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝沙箱支付服务：电脑网站支付（page.pay）
 */
@Slf4j
@Service
public class AlipayService {

    @Autowired
    private AlipayClient alipayClient;

    @Autowired
    private AlipayConfigProperties props;

    @Autowired
    private ShopOrderService orderService;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    /**
     * 创建支付表单（前端新窗口写入并自动提交，跳转支付宝收银台）
     */
    public String createPagePayForm(ShopOrder order) {
        if (!props.isConfigured()) {
            throw new IllegalStateException("支付宝沙箱密钥未配置，请先在 application.yaml 中填写 " +
                    "alipay.merchant-private-key 与 alipay.alipay-public-key");
        }

        // 订单标题：取第一个商品名
        List<ShopOrderItem> items = orderItemMapper.selectByOrderId(order.getId());
        String subject = "Feuerwerk商城-";
        if (items != null && !items.isEmpty()) {
            subject += items.get(0).getProductName() + (items.size() > 1 ? "等" + items.size() + "件商品" : "");
        } else {
            subject += "订单" + order.getOrderNo();
        }

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(props.getNotifyUrl());
        request.setReturnUrl(props.getReturnUrl());

        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", order.getOrderNo());
        biz.put("total_amount", order.getTotalAmount().toPlainString());
        biz.put("subject", subject);
        biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
        request.setBizContent(com.example.feuerwerkagent.util.JsonUtil.toJson(biz));

        try {
            // pageExecute返回包含自动提交脚本的完整HTML表单页
            return alipayClient.pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            log.error("创建支付宝支付表单失败", e);
            throw new IllegalStateException("创建支付订单失败：" + e.getErrMsg());
        }
    }

    /**
     * 校验异步通知签名并处理支付结果
     *
     * @return 处理结果：success / failure（按支付宝要求返回纯文本）
     */
    public String handleNotify(Map<String, String> params) {
        try {
            // 1. 验签
            boolean signOk = AlipaySignature.rsaCheckV1(params, props.getAlipayPublicKey(),
                    StandardCharsets.UTF_8.name(), props.getSignType());
            if (!signOk) {
                log.warn("支付宝异步通知验签失败: {}", params);
                return "failure";
            }

            // 2. 校验appid与卖家身份
            if (!props.getAppId().equals(params.get("app_id"))) {
                log.warn("支付宝异步通知app_id不匹配: {}", params.get("app_id"));
                return "failure";
            }
            if (props.getSellerId() != null && !props.getSellerId().isBlank()
                    && !props.getSellerId().equals(params.get("seller_id"))) {
                log.warn("支付宝异步通知seller_id不匹配: {}", params.get("seller_id"));
                return "failure";
            }

            String orderNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            log.info("支付宝异步通知: orderNo={}, tradeNo={}, status={}", orderNo, tradeNo, tradeStatus);

            // 3. 交易成功才处理
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                BigDecimal amount = new BigDecimal(params.get("total_amount"));
                boolean processed = orderService.handlePaymentSuccess(orderNo, tradeNo, amount);
                log.info("支付成功处理结果: orderNo={}, processed={}", orderNo, processed);
            }
            // 金额不匹配等场景 handlePaymentSuccess 返回false，但通知本身合法，返回success防止重发
            return "success";
        } catch (Exception e) {
            log.error("处理支付宝异步通知异常", e);
            return "failure";
        }
    }

    /**
     * 主动查单（本地无公网时，前端轮询/用户点击触发）
     */
    public Map<String, Object> queryOrder(String orderNo) {
        Map<String, Object> result = new HashMap<>();
        ShopOrder order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        // 已是终态直接返回
        if (order.getStatus() != ShopOrder.STATUS_PENDING_PAYMENT) {
            result.put("tradeStatus", "已支付");
            result.put("orderStatus", order.getStatus());
            result.put("orderStatusDesc", order.getStatusDesc());
            return result;
        }

        if (!props.isConfigured()) {
            throw new IllegalStateException("支付宝沙箱密钥未配置");
        }

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        Map<String, Object> biz = new HashMap<>();
        biz.put("out_trade_no", orderNo);
        request.setBizContent(com.example.feuerwerkagent.util.JsonUtil.toJson(biz));

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                // 未支付（ACQ.TRADE_NOT_EXIST等）
                result.put("tradeStatus", "WAIT_BUYER_PAY");
                result.put("orderStatus", order.getStatus());
                result.put("orderStatusDesc", order.getStatusDesc());
                return result;
            }
            String tradeStatus = response.getTradeStatus();
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                BigDecimal amount = new BigDecimal(response.getTotalAmount());
                orderService.handlePaymentSuccess(orderNo, response.getTradeNo(), amount);
                order = orderService.getByOrderNo(orderNo);
                result.put("tradeStatus", tradeStatus);
                result.put("orderStatus", order.getStatus());
                result.put("orderStatusDesc", order.getStatusDesc());
            } else {
                result.put("tradeStatus", tradeStatus);
                result.put("orderStatus", order.getStatus());
                result.put("orderStatusDesc", order.getStatusDesc());
            }
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝查单失败: {}", e.getErrMsg(), e);
            throw new IllegalStateException("查询支付状态失败：" + e.getErrMsg());
        }
    }
}
