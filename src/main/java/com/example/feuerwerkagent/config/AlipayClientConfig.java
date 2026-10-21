package com.example.feuerwerkagent.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝客户端装配
 */
@Configuration
public class AlipayClientConfig {

    @Bean
    public AlipayClient alipayClient(AlipayConfigProperties props) {
        return new DefaultAlipayClient(
                props.getGatewayUrl(),
                props.getAppId(),
                props.getMerchantPrivateKey(),
                "json",
                "UTF-8",
                props.getAlipayPublicKey(),
                props.getSignType());
    }
}
