package com.chh.campustix.common.event;

/**
 * 库存释放事件（订单取消/超时关单后发布，事务提交后触发）。
 * 候补模块监听该事件唤醒队首用户。
 */
public record StockReleasedEvent(Long sessionId, int quantity) {
}
