package com.chh.campustix.common.mq;

/**
 * 延迟关单抽象：RocketMQ 延迟消息（默认）/ Redisson 延迟队列（资源受限降级）双实现。
 * 另有 @Scheduled 定时兜底，三者幂等收敛到同一个 CAS 关单入口。
 */
public interface DelayQueue {

    void sendCloseOrder(String orderNo, long delaySeconds);
}
