package com.chh.campustix.module.waitlist;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.waitlist.dto.WaitlistVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping("/{sessionId}/join")
    public Result<Boolean> join(@PathVariable Long sessionId) {
        waitlistService.join(sessionId);
        return Result.ok(true);
    }

    @PostMapping("/{sessionId}/cancel")
    public Result<Boolean> cancel(@PathVariable Long sessionId) {
        waitlistService.cancel(sessionId);
        return Result.ok(true);
    }

    @GetMapping("/mine")
    public Result<List<WaitlistVO>> mine() {
        return Result.ok(waitlistService.mine());
    }
}
