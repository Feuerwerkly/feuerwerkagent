package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.config.ShopAuthInterceptor;
import com.example.feuerwerkagent.dto.ApiResponse;
import com.example.feuerwerkagent.entity.ShopOrder;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.service.AlipayService;
import com.example.feuerwerkagent.service.ShopOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝沙箱支付接口
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/alipay")
@CrossOrigin(origins = "*")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private ShopOrderService orderService;

    /**
     * 发起支付：返回支付宝收银台跳转表单（需登录，订单须属于当前用户且待支付）
     */
    @PostMapping("/pay/{orderId}")
    public ApiResponse<Map<String, Object>> pay(HttpServletRequest request, @PathVariable Long orderId) {
        try {
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            Map<String, Object> order = orderService.myOrderDetail(user.getId(), orderId);
            if ((int) order.get("status") != ShopOrder.STATUS_PENDING_PAYMENT) {
                return ApiResponse.error(400, "订单不是待支付状态");
            }
            ShopOrder full = orderService.getByOrderNo((String) order.get("orderNo"));
            String form = alipayService.createPagePayForm(full);

            Map<String, Object> data = new HashMap<>();
            data.put("payForm", form);
            data.put("orderNo", order.get("orderNo"));
            return ApiResponse.success(data);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("发起支付失败", e);
            return ApiResponse.error("发起支付失败：" + e.getMessage());
        }
    }

    /**
     * 支付宝异步通知（POST，支付宝服务器回调，不带登录token，拦截器已放行）
     * 按支付宝规范返回纯文本 success / failure
     */
    @PostMapping("/notify")
    public void notifyCallback(HttpServletRequest request, HttpServletResponse response) throws Exception {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, String.join(",", values));
            }
        });
        String result = alipayService.handleNotify(params);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(result);
    }

    /**
     * 主动查单（本地开发无公网时由前端调用，确认支付结果）
     */
    @GetMapping("/query")
    public ApiResponse<Map<String, Object>> query(HttpServletRequest request,
                                                  @RequestParam String orderNo) {
        try {
            // 仅允许查询自己的订单
            ShopUser user = ShopAuthInterceptor.currentUser(request);
            ShopOrder order = orderService.getByOrderNo(orderNo);
            if (order == null || !order.getUserId().equals(user.getId())) {
                return ApiResponse.error(404, "订单不存在");
            }
            return ApiResponse.success(alipayService.queryOrder(orderNo));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("查单失败", e);
            return ApiResponse.error("查单失败：" + e.getMessage());
        }
    }
}
