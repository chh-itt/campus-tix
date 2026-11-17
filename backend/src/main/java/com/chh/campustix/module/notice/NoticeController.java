package com.chh.campustix.module.notice;

import com.chh.campustix.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping("/mine")
    public Result<List<Notice>> mine(@RequestParam(defaultValue = "1") long pageNo,
                                     @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(noticeService.mine(pageNo, pageSize));
    }

    @PostMapping("/{id}/read")
    public Result<Boolean> read(@PathVariable Long id) {
        noticeService.markRead(id);
        return Result.ok(true);
    }
}
