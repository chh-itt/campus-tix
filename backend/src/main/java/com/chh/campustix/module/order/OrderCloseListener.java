package com.chh.campustix.module.order;

import com.chh.campustix.common.mq.RocketMqDelayQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 延迟关单消费者：消息早到/重复均由 closeIfExpiredByMessage 幂等处理（仅 rocketmq 延迟实现时装配） */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campustix.order.delay-impl", havingValue = "rocketmq")
@RocketMQMessageListener(topic = RocketMqDelayQueue.TOPIC, consumerGroup = "campustix-order-close-consumer")
public class OrderCloseListener implements RocketMQListener<String> {

    private final OrderService orderService;

    @Override
    public void onMessage(String orderNo) {
        log.info("收到延迟关单消息 orderNo={}", orderNo);
        orderService.closeIfExpiredByMessage(orderNo);
    }
}
