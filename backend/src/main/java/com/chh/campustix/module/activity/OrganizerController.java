package com.chh.campustix.module.activity;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.activity.dto.ActivityCreateReq;
import com.chh.campustix.module.activity.dto.CheckInReq;
import com.chh.campustix.module.activity.dto.SessionCreateReq;
import com.chh.campustix.module.ticket.dto.TicketVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/organizer")
@RequiredArgsConstructor
public class OrganizerController {

    private final OrganizerService organizerService;

    @GetMapping("/activities")
    public Result<List<Activity>> myActivities() {
        return Result.ok(organizerService.myActivities());
    }

    @PostMapping("/activities")
    public Result<Long> create(@Valid @RequestBody ActivityCreateReq req) {
        return Result.ok(organizerService.createActivity(req));
    }

    @PutMapping("/activities/{id}")
    public Result<Boolean> update(@PathVariable Long id, @Valid @RequestBody ActivityCreateReq req) {
        organizerService.updateActivity(id, req);
        return Result.ok(true);
    }

    @PostMapping("/activities/{id}/publish")
    public Result<Boolean> publish(@PathVariable Long id) {
        organizerService.publish(id);
        return Result.ok(true);
    }

    @PostMapping("/activities/{id}/offline")
    public Result<Boolean> offline(@PathVariable Long id) {
        organizerService.offline(id);
        return Result.ok(true);
    }

    @GetMapping("/activities/{id}/sessions")
    public Result<List<ActivitySession>> sessions(@PathVariable Long id) {
        organizerService.myActivities(); // 权限入口校验
        return Result.ok(organizerService.sessionsOf(id));
    }

    @PostMapping("/activities/{id}/sessions")
    public Result<Boolean> addSession(@PathVariable Long id, @Valid @RequestBody SessionCreateReq req) {
        organizerService.addSession(id, req);
        return Result.ok(true);
    }

    @PostMapping("/check-in")
    public Result<TicketVO> checkIn(@Valid @RequestBody CheckInReq req) {
        return Result.ok(organizerService.checkIn(req));
    }
}
