package com.chh.campustix.module.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ActivityDetailVO(Long id, String title, String category, String cover,
                               String description, String venue, List<SessionVO> sessions) {

    public record SessionVO(Long id, String title, LocalDateTime startTime, LocalDateTime endTime,
                            LocalDateTime saleStart, LocalDateTime saleEnd, BigDecimal price,
                            Integer quota, Integer remaining, Integer sold, String status) {
    }
}
