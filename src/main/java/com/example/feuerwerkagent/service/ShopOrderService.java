package com.example.feuerwerkagent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.feuerwerkagent.dto.ShopOrderCreateRequest;
import com.example.feuerwerkagent.entity.ShopOrder;
import com.example.feuerwerkagent.entity.ShopOrderItem;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.mapper.ShopOrderItemMapper;
import com.example.feuerwerkagent.mapper.ShopOrderMapper;
import com.example.feuerwerkagent.mapper.ShopProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务：下单（扣库存）/支付成功（加销量）/取消（回库存）/发货/收货
 */
@Service
public class ShopOrderService {

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private ShopOrderMapper orderMapper;

    @Autowired
    private ShopOrderItemMapper orderItemMapper;

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopCartService cartService;

    /**
     * 创建订单：价格取数据库实时价，库存原子扣减，任一失败整体回滚
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, ShopOrderCreateRequest req) {
        if (req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("订单商品不能为空");
        }
        if (!StringUtils.hasText(req.getReceiverName())) {
            throw new IllegalArgumentException("请填写收货人姓名");
        }
        if (!StringUtils.hasText(req.getReceiverPhone())) {
            throw new IllegalArgumentException("请填写收货电话");
        }
        if (!StringUtils.hasText(req.getReceiverAddress())) {
            throw new IllegalArgumentException("请填写收货地址");
        }

        // 合并同商品数量
        Map<Long, Integer> qtyMap = new HashMap<>();
        for (ShopOrderCreateRequest.Item item : req.getItems()) {
            if (item.getProductId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException("订单商品参数非法");
            }
            qtyMap.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }

        // 逐项校验并原子扣库存
        List<ShopOrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : qtyMap.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            ShopProduct product = productMapper.selectById(productId);
            if (product == null || product.getStatus() == null || product.getStatus() != 1) {
                throw new IllegalArgumentException("商品不存在或已下架（id=" + productId + "）");
            }
            int affected = productMapper.deductStock(productId, quantity);
            if (affected == 0) {
                throw new IllegalArgumentException("【" + product.getName() + "】库存不足，剩余库存："
                        + Math.max(product.getStock() - quantity + 1, 0));
            }

            ShopOrderItem orderItem = new ShopOrderItem();
            orderItem.setProductId(productId);
            orderItem.setProductName(product.getName());
            orderItem.setProductImage(product.getMainImage());
            orderItem.setPrice(product.getPrice());
            orderItem.setQuantity(quantity);
            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            orderItem.setSubtotal(subtotal);
            orderItems.add(orderItem);
            total = total.add(subtotal);
        }

        // 生成订单
        ShopOrder order = new ShopOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setStatus(ShopOrder.STATUS_PENDING_PAYMENT);
        order.setReceiverName(req.getReceiverName().trim());
        order.setReceiverPhone(req.getReceiverPhone().trim());
        order.setReceiverAddress(req.getReceiverAddress().trim());
        order.setRemark(req.getRemark());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insert(order);

        for (ShopOrderItem item : orderItems) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 购物车结算后移除对应商品
        if (Boolean.TRUE.equals(req.getFromCart())) {
            cartService.removeByProducts(userId, new ArrayList<>(qtyMap.keySet()));
        }

        return detailMap(order, orderItems);
    }

    /**
     * 我的订单列表（含明细）
     */
    public List<Map<String, Object>> myOrders(Long userId) {
        List<ShopOrder> orders = orderMapper.selectList(new QueryWrapper<ShopOrder>()
                .eq("user_id", userId).orderByDesc("create_time"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ShopOrder order : orders) {
            result.add(detailMap(order, orderItemMapper.selectByOrderId(order.getId())));
        }
        return result;
    }

    /**
     * 订单详情（校验归属）
     */
    public Map<String, Object> myOrderDetail(Long userId, Long orderId) {
        ShopOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        return detailMap(order, orderItemMapper.selectByOrderId(orderId));
    }

    /**
     * 取消订单（仅待支付可取消，回滚库存）
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long orderId) {
        ShopOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() != ShopOrder.STATUS_PENDING_PAYMENT) {
            throw new IllegalArgumentException("当前状态不可取消");
        }

        // 条件更新，防并发重复取消
        ShopOrder update = new ShopOrder();
        update.setStatus(ShopOrder.STATUS_CANCELLED);
        update.setUpdateTime(LocalDateTime.now());
        int affected = orderMapper.update(update, new QueryWrapper<ShopOrder>()
                .eq("id", orderId).eq("status", ShopOrder.STATUS_PENDING_PAYMENT));
        if (affected == 0) {
            throw new IllegalArgumentException("订单状态已变化，请刷新");
        }

        // 回滚库存
        for (ShopOrderItem item : orderItemMapper.selectByOrderId(orderId)) {
            productMapper.restoreStock(item.getProductId(), item.getQuantity());
        }
    }

    /**
     * 确认收货（已发货 -> 已完成）
     */
    public void confirm(Long userId, Long orderId) {
        ShopOrder order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() != ShopOrder.STATUS_SHIPPED) {
            throw new IllegalArgumentException("当前状态不可确认收货");
        }
        ShopOrder update = new ShopOrder();
        update.setStatus(ShopOrder.STATUS_COMPLETED);
        update.setFinishTime(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        orderMapper.update(update, new QueryWrapper<ShopOrder>()
                .eq("id", orderId).eq("status", ShopOrder.STATUS_SHIPPED));
    }

    /**
     * 支付成功处理（幂等）：仅待支付订单生效，同时累加销量
     *
     * @param orderNo   商户订单号
     * @param tradeNo   支付宝交易号
     * @param amount    支付金额（校验一致性），可为null跳过校验
     * @return true=本次成功把订单置为已支付；false=订单不存在/金额不符/已处理过
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentSuccess(String orderNo, String tradeNo, BigDecimal amount) {
        ShopOrder order = orderMapper.selectOne(new QueryWrapper<ShopOrder>().eq("order_no", orderNo));
        if (order == null) {
            return false;
        }
        if (amount != null && order.getTotalAmount().compareTo(amount) != 0) {
            return false;
        }
        int affected = orderMapper.markPaid(orderNo, tradeNo, LocalDateTime.now());
        if (affected == 0) {
            // 已处理过（幂等）
            return false;
        }
        // 累加商品销量
        for (ShopOrderItem item : orderItemMapper.selectByOrderId(order.getId())) {
            productMapper.increaseSales(item.getProductId(), item.getQuantity());
        }
        return true;
    }

    /**
     * 按订单号取订单（支付流程用）
     */
    public ShopOrder getByOrderNo(String orderNo) {
        return orderMapper.selectOne(new QueryWrapper<ShopOrder>().eq("order_no", orderNo));
    }

    /**
     * 管理员：全部订单分页
     */
    public Page<ShopOrder> adminOrders(int page, int size, Integer status) {
        QueryWrapper<ShopOrder> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");
        return orderMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 管理员：发货
     */
    public void adminShip(Long orderId) {
        ShopOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() != ShopOrder.STATUS_PAID) {
            throw new IllegalArgumentException("仅已支付订单可发货");
        }
        ShopOrder update = new ShopOrder();
        update.setStatus(ShopOrder.STATUS_SHIPPED);
        update.setDeliveryTime(LocalDateTime.now());
        update.setUpdateTime(LocalDateTime.now());
        orderMapper.update(update, new QueryWrapper<ShopOrder>()
                .eq("id", orderId).eq("status", ShopOrder.STATUS_PAID));
    }

    /**
     * 管理员：经营统计
     */
    public Map<String, Object> adminStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("paidOrderCount", orderMapper.selectCount(new QueryWrapper<ShopOrder>()
                .in("status", ShopOrder.STATUS_PAID, ShopOrder.STATUS_SHIPPED, ShopOrder.STATUS_COMPLETED)));
        stats.put("pendingOrderCount", orderMapper.selectCount(new QueryWrapper<ShopOrder>()
                .eq("status", ShopOrder.STATUS_PENDING_PAYMENT)));

        // 总销售额
        List<ShopOrder> paid = orderMapper.selectList(new QueryWrapper<ShopOrder>()
                .in("status", ShopOrder.STATUS_PAID, ShopOrder.STATUS_SHIPPED, ShopOrder.STATUS_COMPLETED)
                .select("total_amount"));
        BigDecimal totalSales = BigDecimal.ZERO;
        for (ShopOrder o : paid) {
            totalSales = totalSales.add(o.getTotalAmount());
        }
        stats.put("totalSales", totalSales);

        // 商品数/总库存
        List<ShopProduct> products = productMapper.selectList(new QueryWrapper<ShopProduct>().select("stock"));
        stats.put("productCount", products.size());
        int totalStock = products.stream().mapToInt(p -> p.getStock() == null ? 0 : p.getStock()).sum();
        stats.put("totalStock", totalStock);
        return stats;
    }

    /**
     * 生成订单号：FW + 时间戳 + 4位随机数（支付宝out_trade_no，最长64）
     */
    private String generateOrderNo() {
        return "FW" + LocalDateTime.now().format(ORDER_NO_FORMAT)
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }

    /**
     * 订单 + 明细组装
     */
    private Map<String, Object> detailMap(ShopOrder order, List<ShopOrderItem> items) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", order.getId());
        map.put("orderNo", order.getOrderNo());
        map.put("totalAmount", order.getTotalAmount());
        map.put("status", order.getStatus());
        map.put("statusDesc", order.getStatusDesc());
        map.put("receiverName", order.getReceiverName());
        map.put("receiverPhone", order.getReceiverPhone());
        map.put("receiverAddress", order.getReceiverAddress());
        map.put("remark", order.getRemark());
        map.put("tradeNo", order.getTradeNo());
        map.put("paymentTime", order.getPaymentTime());
        map.put("deliveryTime", order.getDeliveryTime());
        map.put("finishTime", order.getFinishTime());
        map.put("createTime", order.getCreateTime());
        map.put("items", items);
        return map;
    }
}
