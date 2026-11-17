package com.chh.campustix.common.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "campustix")
public class CampustixProps {

    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Order order = new Order();
    private MockPay mockPay = new MockPay();
    private Alipay alipay = new Alipay();
    private Ai ai = new Ai();

    @Data
    public static class Jwt {
        private String secret;
        private int expireHours = 72;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }

    @Data
    public static class Order {
        /** 下单后支付窗口（分钟） */
        private int payTtlMinutes = 2;
        /** RocketMQ 延迟消息档位（1s,5s,10s,30s,1m,2m,...,30m,1h,2h） */
        private int delayLevel = 6;
        /** 延迟队列实现：rocketmq | redis */
        private String delayImpl = "rocketmq";
        /** 候补转正后的限时支付窗口（分钟） */
        private int waitlistPayTtlMinutes = 10;
    }

    @Data
    public static class MockPay {
        private String secret = "dev-mock-gateway-secret";
    }

    @Data
    public static class Alipay {
        private boolean enabled = false;
        private String appId;
        private String appPrivateKey;
        private String alipayPublicKey;
        private String gateway = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
    }

    @Data
    public static class Ai {
        private DeepSeek deepseek = new DeepSeek();
        private int rateLimitPerMinute = 10;

        @Data
        public static class DeepSeek {
            private String baseUrl = "https://api.deepseek.com";
            private String apiKey = "";
            private String model = "deepseek-chat";
        }
    }
}
