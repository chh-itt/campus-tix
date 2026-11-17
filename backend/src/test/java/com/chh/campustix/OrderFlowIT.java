package com.chh.campustix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全链路集成测试（Testcontainers：MySQL + Redis，延迟关单走 Redis 实现，不依赖 RocketMQ）：
 * 1) 并发下单防超卖：quota=2 的场次 4 人并发抢 → 恰好 2 单成功 2 单 SOLD_OUT，DB remaining=0；
 * 2) 正常流：下单 → 模拟支付回调 → 出票 → 主办方核销 → 重复核销被 CAS 拒绝。
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderFlowIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("campustix").withUsername("ct").withPassword("ct");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // 测试环境用 Redis 延迟队列（自动禁用 RocketMQ 监听器与延迟队列）
        registry.add("campustix.order.delay-impl", () -> "redis");
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    ObjectMapper om;

    /** 种子数据：act4「闭门研讨」quota=2，act1 第一个场次 quota=200 */
    static final AtomicReference<Long> QUOTA2_SESSION = new AtomicReference<>();
    static final AtomicReference<Long> BIG_SESSION = new AtomicReference<>();

    void resolveSeedSessions() throws Exception {
        if (QUOTA2_SESSION.get() != null) return;
        ResponseEntity<String> resp = rest.getForEntity("/api/activities", String.class);
        JsonNode root = om.readTree(resp.getBody());
        for (JsonNode a : root.get("data")) {
            JsonNode detail = om.readTree(rest.getForEntity("/api/activities/" + a.get("id").asText(), String.class).getBody());
            for (JsonNode s : detail.get("data").get("sessions")) {
                if (s.get("quota").asInt() == 2 && QUOTA2_SESSION.get() == null) {
                    QUOTA2_SESSION.set(s.get("id").asLong());
                } else if (s.get("quota").asInt() >= 100 && BIG_SESSION.get() == null) {
                    BIG_SESSION.set(s.get("id").asLong());
                }
            }
        }
        assertThat(QUOTA2_SESSION.get()).isNotNull();
        assertThat(BIG_SESSION.get()).isNotNull();
    }

    /** 注册新用户并返回 token */
    String newUser() throws Exception {
        String u = "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, String> body = Map.of("username", u, "password", "123456");
        ResponseEntity<String> resp = rest.postForEntity("/api/auth/register", json(body), String.class);
        JsonNode root = om.readTree(resp.getBody());
        assertThat(root.get("code").asInt()).isZero();
        return root.get("data").get("token").asText();
    }

    @SuppressWarnings("unchecked")
    HttpEntity<Map<String, Object>> json(Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>((Map<String, Object>) body, headers);
    }

    @SuppressWarnings("unchecked")
    HttpEntity<Map<String, Object>> jsonWithToken(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>((Map<String, Object>) body, headers);
    }

    int createOrder(String token, long sessionId) throws Exception {
        String idem = rest.exchange("/api/orders/token", org.springframework.http.HttpMethod.GET,
                jsonWithToken(null, token), String.class).getBody();
        String idemToken = om.readTree(idem).get("data").asText();
        ResponseEntity<String> resp = rest.postForEntity("/api/orders",
                jsonWithToken(Map.of("sessionId", sessionId, "quantity", 1, "idempotencyToken", idemToken), token), String.class);
        return om.readTree(resp.getBody()).get("code").asInt();
    }

    @Test
    @Order(1)
    void concurrentOrders_neverOversell() throws Exception {
        resolveSeedSessions();
        long sessionId = QUOTA2_SESSION.get();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<Integer>> futures = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tokens.add(newUser());
        }
        for (int i = 0; i < threads; i++) {
            String t = tokens.get(i);
            futures.add(pool.submit((Callable<Integer>) () -> createOrder(t, sessionId)));
        }
        int success = 0, soldOut = 0;
        for (Future<Integer> f : futures) {
            int code = f.get();
            if (code == 0) success++;
            if (code == 410) soldOut++;
        }
        pool.shutdown();
        assertThat(success).as("仅 2 个名额，成功数必须恰好为 2").isEqualTo(2);
        assertThat(soldOut).as("其余全部 SOLD_OUT(410)").isEqualTo(threads - 2);
    }

    @Test
    @Order(2)
    void payThenTicketThenCheckIn() throws Exception {
        resolveSeedSessions();
        String token = newUser();
        long sessionId = BIG_SESSION.get();

        // 下单
        assertThat(createOrder(token, sessionId)).isZero();

        // 查我的订单拿 orderNo
        String mine = rest.exchange("/api/orders?pageNo=1&pageSize=5", org.springframework.http.HttpMethod.GET,
                jsonWithToken(null, token), String.class).getBody();
        JsonNode order = om.readTree(mine).get("data").get(0);
        String orderNo = order.get("orderNo").asText();
        assertThat(order.get("status").asText()).isEqualTo("CREATED");

        // 获取支付链接（模拟收银台）
        String payResp = rest.exchange("/api/orders/" + orderNo + "/pay", org.springframework.http.HttpMethod.POST,
                jsonWithToken(null, token), String.class).getBody();
        JsonNode payVo = om.readTree(payResp).get("data");
        assertThat(payVo.get("payType").asText()).isEqualTo("MOCK");
        String payUrl = payVo.get("payUrl").asText();
        assertThat(payUrl).contains("/api/payments/mock/pay").contains("sign=");

        // 从收银台 URL 提取参数，模拟网关回调
        java.net.URI uri = java.net.URI.create(payUrl);
        String[] parts = uri.getQuery().split("&");
        String orderNoQ = "", amount = "", tradeNo = "", sign = "";
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            switch (kv[0]) {
                case "orderNo" -> orderNoQ = kv[1];
                case "amount" -> amount = kv[1];
                case "tradeNo" -> tradeNo = kv[1];
                case "sign" -> sign = kv[1];
            }
        }
        HttpHeaders form = new HttpHeaders();
        form.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String notifyBody = "orderNo=" + orderNoQ + "&amount=" + amount + "&tradeNo=" + tradeNo + "&sign=" + sign;
        ResponseEntity<String> notify = rest.postForEntity("/api/payments/mock/notify",
                new HttpEntity<>(notifyBody, form), String.class);
        assertThat(notify.getBody()).contains("支付成功");

        // 订单变 PAID、出票
        String mine2 = rest.exchange("/api/orders/" + orderNo, org.springframework.http.HttpMethod.GET,
                jsonWithToken(null, token), String.class).getBody();
        assertThat(om.readTree(mine2).get("data").get("status").asText()).isEqualTo("PAID");

        String tickets = rest.exchange("/api/tickets/mine", org.springframework.http.HttpMethod.GET,
                jsonWithToken(null, token), String.class).getBody();
        JsonNode ticket = om.readTree(tickets).get("data").get(0);
        assertThat(ticket.get("status").asText()).isEqualTo("VALID");
        String ticketNo = ticket.get("ticketNo").asText();

        // 主办方核销 → 成功；重复核销 → ORDER_STATE_ERROR(412)
        String orgToken = login("organizer");
        ResponseEntity<String> first = rest.exchange("/api/organizer/check-in", org.springframework.http.HttpMethod.POST,
                jsonWithToken(Map.of("ticketNo", ticketNo), orgToken), String.class);
        assertThat(om.readTree(first.getBody()).get("code").asInt()).isZero();

        ResponseEntity<String> second = rest.exchange("/api/organizer/check-in", org.springframework.http.HttpMethod.POST,
                jsonWithToken(Map.of("ticketNo", ticketNo), orgToken), String.class);
        assertThat(om.readTree(second.getBody()).get("code").asInt()).isEqualTo(412);
    }

    String login(String username) throws Exception {
        ResponseEntity<String> resp = rest.postForEntity("/api/auth/login",
                json(Map.of("username", username, "password", "123456")), String.class);
        return om.readTree(resp.getBody()).get("data").get("token").asText();
    }
}
