package com.example.feuerwerkagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙箱配置（application.yaml 中 alipay.* ）
 */
@Data
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfigProperties {

    /**
     * 沙箱应用ID：9021000169626849
     */
    private String appId;

    /**
     * 沙箱网关：https://openapi-sandbox.dl.alipaydev.com/gateway.do
     */
    private String gatewayUrl;

    /**
     * 应用私钥（控制台-接口加签方式-查看-应用私钥PKCS8）
     */
    private String merchantPrivateKey;

    /**
     * 支付宝公钥（控制台-接口加签方式-查看-支付宝公钥）
     */
    private String alipayPublicKey;

    /**
     * 商家账号PID（校验通知卖家身份用）
     */
    private String sellerId;

    /**
     * 签名类型
     */
    private String signType = "RSA2";

    /**
     * 异步通知地址
     */
    private String notifyUrl;

    /**
     * 同步跳转地址（前端）
     */
    private String returnUrl;

    /**
     * 密钥是否已正确配置
     */
    public boolean isConfigured() {
        return merchantPrivateKey != null && !merchantPrivateKey.isBlank()
                && !merchantPrivateKey.contains("CHANGE_ME")
                && alipayPublicKey != null && !alipayPublicKey.isBlank()
                && !alipayPublicKey.contains("CHANGE_ME");
    }
}
