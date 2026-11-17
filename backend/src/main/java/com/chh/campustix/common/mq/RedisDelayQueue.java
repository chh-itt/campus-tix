package com.chh.campustix.common.mq;

import com.chh.campustix.module.order.OrderService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redisson 延迟队列实现：delay-impl=redis 时启用（无 RocketMQ 的环境）。
 * 独立守护线程阻塞消费，消费失败不重投，由定时兜底任务收敛。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "campustix.order.delay-impl", havingValue = "redis")
public class RedisDelayQueue implements DelayQueue {

    public static final String QUEUE = "campustix:delay:order-close";

    private final RedissonClient redisson;
    private final ObjectProvider<OrderService> orderServiceProvider;
    private final RBlockingQueue<String> destQueue;
    private final RDelayedQueue<String> delayedQueue;
    private final Thread worker;

    public RedisDelayQueue(RedissonClient redisson, ObjectProvider<OrderService> orderServiceProvider) {
        this.redisson = redisson;
        this.orderServiceProvider = orderServiceProvider;
        this.destQueue = redisson.getBlockingQueue(QUEUE);
        this.delayedQueue = redisson.getDelayedQueue(destQueue);
        this.worker = new Thread(this::loop, "order-close-delay-poller");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void sendCloseOrder(String orderNo, long delaySeconds) {
        delayedQueue.offer(orderNo, Math.max(delaySeconds, 1), TimeUnit.SECONDS);
        log.info("Redis 延迟关单已入队 orderNo={} delaySeconds={}", orderNo, delaySeconds);
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String orderNo = destQueue.take();
                OrderService orderService = orderServiceProvider.getIfAvailable();
                if (orderService != null) {
                    orderService.closeIfExpiredByMessage(orderNo);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("延迟关单消费失败（交由定时兜底）", e);
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        worker.interrupt();
    }
}
