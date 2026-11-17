package com.chh.campustix.module.waitlist.dto;

import java.time.LocalDateTime;

public record WaitlistVO(Long id, Long sessionId, String status,
                         String notifiedOrderNo, LocalDateTime createdAt) {
}
