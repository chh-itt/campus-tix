package com.chh.campustix.module.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chh.campustix.common.security.UserContext;
import com.chh.campustix.module.activity.Activity;
import com.chh.campustix.module.activity.ActivityMapper;
import com.chh.campustix.module.activity.ActivitySession;
import com.chh.campustix.module.activity.ActivitySessionMapper;
import com.chh.campustix.module.ticket.dto.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketMapper ticketMapper;
    private final ActivityMapper activityMapper;
    private final ActivitySessionMapper sessionMapper;

    public List<TicketVO> mine() {
        Long userId = UserContext.requireId();
        List<Ticket> tickets = ticketMapper.selectList(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getUserId, userId)
                .orderByDesc(Ticket::getCreatedAt)
                .last("LIMIT 200"));
        if (tickets.isEmpty()) {
            return List.of();
        }
        Set<Long> activityIds = tickets.stream().map(Ticket::getActivityId).collect(Collectors.toSet());
        Set<Long> sessionIds = tickets.stream().map(Ticket::getSessionId).collect(Collectors.toSet());
        Map<Long, Activity> amap = activityMapper.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        Map<Long, ActivitySession> smap = sessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(ActivitySession::getId, Function.identity()));
        return tickets.stream()
                .map(t -> TicketVO.from(t, amap.get(t.getActivityId()), smap.get(t.getSessionId())))
                .collect(Collectors.toList());
    }
}
