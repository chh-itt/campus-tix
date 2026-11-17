package com.chh.campustix.module.order.dto;

import com.chh.campustix.module.order.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreateVO(
        String orderNo,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        LocalDateTime payExpireAt) {

    public static OrderCreateVO from(Order o) {
        return new OrderCreateVO(o.getOrderNo(), o.getQuantity(), o.getUnitPrice(),
                o.getTotalAmount(), o.getStatus(), o.getPayExpireAt());
    }
}
