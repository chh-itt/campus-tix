package com.chh.campustix.module.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chh.campustix.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    public void create(Long userId, String title, String content, String bizNo) {
        Notice n = new Notice();
        n.setUserId(userId);
        n.setTitle(title);
        n.setContent(content);
        n.setIsRead(0);
        n.setBizNo(bizNo);
        noticeMapper.insert(n);
    }

    public List<Notice> mine(long pageNo, long pageSize) {
        Long userId = UserContext.requireId();
        return noticeMapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 50)),
                new LambdaQueryWrapper<Notice>()
                        .eq(Notice::getUserId, userId)
                        .orderByDesc(Notice::getCreatedAt)).getRecords();
    }

    public void markRead(Long id) {
        noticeMapper.update(null, new LambdaUpdateWrapper<Notice>()
                .eq(Notice::getId, id)
                .eq(Notice::getUserId, UserContext.requireId())
                .set(Notice::getIsRead, 1));
    }
}
