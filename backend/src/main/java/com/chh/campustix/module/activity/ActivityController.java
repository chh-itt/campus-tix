package com.chh.campustix.module.activity;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.activity.dto.ActivityDetailVO;
import com.chh.campustix.module.activity.dto.ActivityVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping
    public Result<List<ActivityVO>> list(@RequestParam(defaultValue = "1") long pageNo,
                                         @RequestParam(defaultValue = "10") long pageSize,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String keyword) {
        return Result.ok(activityService.list(pageNo, pageSize, category, keyword));
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> detail(@PathVariable Long id) {
        return Result.ok(activityService.detail(id));
    }
}
