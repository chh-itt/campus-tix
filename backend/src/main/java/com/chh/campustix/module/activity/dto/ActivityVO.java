package com.chh.campustix.module.activity.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ActivityVO(Long id, String title, String category, String cover, String venue,
                         BigDecimal minPrice, LocalDateTime earliestStart, Long sessionCount) {
}
