package com.chh.campustix.module.ticket.dto;

import com.chh.campustix.module.activity.Activity;
import com.chh.campustix.module.activity.ActivitySession;
import com.chh.campustix.module.ticket.Ticket;

import java.time.LocalDateTime;

public record TicketVO(
        String ticketNo,
        String status,
        String activityTitle,
        String sessionTitle,
        LocalDateTime startTime,
        String venue,
        LocalDateTime checkedInAt) {

    public static TicketVO from(Ticket t, Activity a, ActivitySession s) {
        return new TicketVO(t.getTicketNo(), t.getStatus(),
                a != null ? a.getTitle() : null,
                s != null ? s.getTitle() : null,
                s != null ? s.getStartTime() : null,
                s != null && a != null ? a.getVenue() : null,
                t.getCheckedInAt());
    }
}
