package com.chh.campustix.common.mq;

import com.chh.campustix.common.props.CampustixProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campustix.order.delay-impl", havingValue = "rocketmq", matchIfMissing = true)
public class RocketMqDelayQueue implements DelayQueue {

    public static final String TOPIC = "ORDER_CLOSE_TOPIC";

    private final RocketMQTemplate rocketMQTemplate;
    private final CampustixProps props;

    @Override
    public void sendCloseOrder(String orderNo, long delaySeconds) {
        try {
            Message<String> msg = MessageBuilder.withPayload(orderNo).setHeader("KEYS", orderNo).build();
            // RocketMQ 4.x/5.x 开源版固定 18 个延迟档位：1s 5s 10s 30s 1m 2m ... 30m 1h 2h
            int level = props.getOrder().getDelayLevel();
            rocketMQTemplate.syncSend(TOPIC, msg, 3000, level);
            log.info("延迟关单消息已投递 orderNo={} delayLevel={}", orderNo, level);
        } catch (Exception e) {
            // 投递失败不阻塞主流程：定时兜底任务会关掉过期单
            log.warn("延迟关单消息投递失败，交由定时兜底 orderNo={}", orderNo, e);
        }
    }
}
