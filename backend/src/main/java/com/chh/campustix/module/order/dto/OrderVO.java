package com.chh.campustix.module.order.dto;

import com.chh.campustix.module.activity.Activity;
import com.chh.campustix.module.activity.ActivitySession;
import com.chh.campustix.module.order.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderVO(
        String orderNo,
        Long activityId,
        Long sessionId,
        String activityTitle,
        String sessionTitle,
        LocalDateTime startTime,
        String venue,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String status,
        String closeReason,
        LocalDateTime payExpireAt,
        LocalDateTime createdAt) {

    public static OrderVO from(Order o, Activity a, ActivitySession s) {
        return new OrderVO(o.getOrderNo(), o.getActivityId(), o.getSessionId(),
                a != null ? a.getTitle() : null,
                s != null ? s.getTitle() : null,
                s != null ? s.getStartTime() : null,
                s != null && a != null ? a.getVenue() : null,
                o.getQuantity(), o.getUnitPrice(), o.getTotalAmount(),
                o.getStatus(), o.getCloseReason(), o.getPayExpireAt(), o.getCreatedAt());
    }
}
