package com.chh.campustix.module.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.module.activity.dto.ActivityDetailVO;
import com.chh.campustix.module.activity.dto.ActivityVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 活动查询：列表 + 详情（Cache-Aside + 空值缓存防穿透 + SETNX 锁防击穿 + 随机 TTL 防雪崩）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final String CACHE_KEY = "cache:activity:";
    private static final String LOCK_KEY = "lock:cache:activity:";

    private final ActivityMapper activityMapper;
    private final ActivitySessionMapper sessionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public List<ActivityVO> list(long pageNo, long pageSize, String category, String keyword) {
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<Activity>()
                .eq(Activity::getStatus, Activity.STATUS_PUBLISHED)
                .eq(StringUtils.hasText(category), Activity::getCategory, category)
                .like(StringUtils.hasText(keyword), Activity::getTitle, keyword)
                .orderByDesc(Activity::getCreatedAt);
        var page = activityMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                        Math.max(pageNo, 1), Math.min(Math.max(pageSize, 1), 20)), wrapper);
        return toVOs(page.getRecords());
    }

    public ActivityDetailVO detail(Long id) {
        String key = CACHE_KEY + id;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                // 空值缓存：防止恶意 id 反复穿透
                if (cached.isBlank()) {
                    throw new BizException(ResultCode.NOT_FOUND, "活动不存在或已下架");
                }
                return objectMapper.readValue(cached, ActivityDetailVO.class);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("活动详情缓存读取失败，降级查库 id={}", id, e);
        }
        // 防击穿：同一活动的并发未命中只放一个线程回源
        boolean locked = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY + id, "1", Duration.ofSeconds(10)));
        if (!locked) {
            return buildDetail(id);
        }
        try {
            Activity a = activityMapper.selectById(id);
            if (a == null || !Activity.STATUS_PUBLISHED.equals(a.getStatus())) {
                stringRedisTemplate.opsForValue().set(key, "", Duration.ofSeconds(30));
                throw new BizException(ResultCode.NOT_FOUND, "活动不存在或已下架");
            }
            ActivityDetailVO vo = buildDetail(a, sessionMapper.selectList(
                    new LambdaQueryWrapper<ActivitySession>()
                            .eq(ActivitySession::getActivityId, id)
                            .orderByAsc(ActivitySession::getStartTime)));
            // 随机 TTL 60~90s 防雪崩
            long ttl = 60 + ThreadLocalRandom.current().nextLong(30);
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(ttl));
            return vo;
        } catch (Exception e) {
            log.error("活动详情回源失败 id={}", id, e);
            return buildDetail(id);
        } finally {
            stringRedisTemplate.delete(LOCK_KEY + id);
        }
    }

    public void evict(Long activityId) {
        stringRedisTemplate.delete(CACHE_KEY + activityId);
    }

    private ActivityDetailVO buildDetail(Long id) {
        Activity a = activityMapper.selectById(id);
        if (a == null || !Activity.STATUS_PUBLISHED.equals(a.getStatus())) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在或已下架");
        }
        return buildDetail(a, sessionMapper.selectList(new LambdaQueryWrapper<ActivitySession>()
                .eq(ActivitySession::getActivityId, id)
                .orderByAsc(ActivitySession::getStartTime)));
    }

    private ActivityDetailVO buildDetail(Activity a, List<ActivitySession> sessions) {
        List<ActivityDetailVO.SessionVO> svos = sessions.stream()
                .map(s -> new ActivityDetailVO.SessionVO(s.getId(), s.getTitle(), s.getStartTime(),
                        s.getEndTime(), s.getSaleStart(), s.getSaleEnd(), s.getPrice(),
                        s.getQuota(), s.getRemaining(), s.getSold(), s.getStatus()))
                .toList();
        return new ActivityDetailVO(a.getId(), a.getTitle(), a.getCategory(), a.getCover(),
                a.getDescription(), a.getVenue(), svos);
    }

    private List<ActivityVO> toVOs(List<Activity> activities) {
        if (activities.isEmpty()) {
            return List.of();
        }
        List<Long> ids = activities.stream().map(Activity::getId).toList();
        Map<Long, List<ActivitySession>> byActivity = sessionMapper.selectList(
                        new LambdaQueryWrapper<ActivitySession>().in(ActivitySession::getActivityId, ids))
                .stream().collect(Collectors.groupingBy(ActivitySession::getActivityId));
        return activities.stream().map(a -> {
            List<ActivitySession> ss = byActivity.getOrDefault(a.getId(), List.of());
            var minPrice = ss.stream().map(ActivitySession::getPrice)
                    .filter(java.util.Objects::nonNull).min(java.math.BigDecimal::compareTo).orElse(null);
            var earliest = ss.stream().map(ActivitySession::getStartTime)
                    .filter(java.util.Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
            return new ActivityVO(a.getId(), a.getTitle(), a.getCategory(), a.getCover(), a.getVenue(),
                    minPrice, earliest, (long) ss.size());
        }).toList();
    }
}
