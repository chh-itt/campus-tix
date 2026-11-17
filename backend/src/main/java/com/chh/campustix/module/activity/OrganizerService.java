package com.chh.campustix.module.activity;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.redis.StockRedisService;
import com.chh.campustix.common.security.LoginUser;
import com.chh.campustix.common.security.UserContext;
import com.chh.campustix.module.activity.dto.ActivityCreateReq;
import com.chh.campustix.module.activity.dto.CheckInReq;
import com.chh.campustix.module.activity.dto.SessionCreateReq;
import com.chh.campustix.module.ticket.Ticket;
import com.chh.campustix.module.ticket.TicketMapper;
import com.chh.campustix.module.ticket.dto.TicketVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/** 主办方/管理端：活动 CRUD、场次管理、发布（初始化 Redis 库存）、扫码核销 */
@Service
@RequiredArgsConstructor
public class OrganizerService {

    private static final String CACHE_KEY = "cache:activity:";

    private final ActivityMapper activityMapper;
    private final ActivitySessionMapper sessionMapper;
    private final TicketMapper ticketMapper;
    private final StockRedisService stockRedis;
    private final StringRedisTemplate stringRedisTemplate;

    public Long createActivity(ActivityCreateReq req) {
        UserContext.requireRole(LoginUser.ROLE_ORGANIZER, LoginUser.ROLE_ADMIN);
        Activity a = new Activity();
        a.setOrganizerId(UserContext.requireId());
        a.setTitle(req.getTitle());
        a.setCategory(req.getCategory() != null ? req.getCategory() : "OTHER");
        a.setCover(req.getCover());
        a.setDescription(req.getDescription());
        a.setVenue(req.getVenue());
        a.setStatus(Activity.STATUS_DRAFT);
        activityMapper.insert(a);
        return a.getId();
    }

    public void updateActivity(Long id, ActivityCreateReq req) {
        Activity a = requireOwn(id);
        a.setTitle(req.getTitle());
        a.setCategory(req.getCategory() != null ? req.getCategory() : a.getCategory());
        a.setCover(req.getCover());
        a.setDescription(req.getDescription());
        a.setVenue(req.getVenue());
        activityMapper.updateById(a);
        evict(id);
    }

    public void addSession(Long activityId, SessionCreateReq req) {
        requireOwn(activityId);
        if (req.getSaleEnd().isAfter(req.getStartTime())) {
            throw new BizException(ResultCode.PARAM_ERROR, "售票截止时间不能晚于开场时间");
        }
        ActivitySession s = new ActivitySession();
        s.setActivityId(activityId);
        s.setTitle(req.getTitle());
        s.setStartTime(req.getStartTime());
        s.setEndTime(req.getEndTime());
        s.setSaleStart(req.getSaleStart() != null ? req.getSaleStart() : LocalDateTime.now());
        s.setSaleEnd(req.getSaleEnd());
        s.setPrice(req.getPrice());
        s.setQuota(req.getQuota());
        s.setRemaining(req.getQuota());
        s.setSold(0);
        s.setStatus(ActivitySession.STATUS_ON_SALE);
        sessionMapper.insert(s);
        stockRedis.init(s.getId(), s.getRemaining());
    }

    public void publish(Long id) {
        requireOwn(id);
        int rows = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, id)
                .in(Activity::getStatus, Activity.STATUS_DRAFT, Activity.STATUS_OFFLINE)
                .set(Activity::getStatus, Activity.STATUS_PUBLISHED));
        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATE_ERROR, "仅草稿/已下线活动可发布");
        }
        // 发布时按 DB 权威剩余量初始化 Redis 库存（自愈入口之一）
        for (ActivitySession s : sessionsOf(id)) {
            if (ActivitySession.STATUS_ON_SALE.equals(s.getStatus())) {
                stockRedis.init(s.getId(), s.getRemaining());
            }
        }
        evict(id);
    }

    public void offline(Long id) {
        requireOwn(id);
        int rows = activityMapper.update(null, new LambdaUpdateWrapper<Activity>()
                .eq(Activity::getId, id)
                .eq(Activity::getStatus, Activity.STATUS_PUBLISHED)
                .set(Activity::getStatus, Activity.STATUS_OFFLINE));
        if (rows == 0) {
            throw new BizException(ResultCode.ORDER_STATE_ERROR, "仅发布中的活动可下线");
        }
        evict(id);
    }

    public List<Activity> myActivities() {
        UserContext.requireRole(LoginUser.ROLE_ORGANIZER, LoginUser.ROLE_ADMIN);
        if (LoginUser.ROLE_ADMIN.equals(UserContext.require().getRole())) {
            return activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                    .orderByDesc(Activity::getCreatedAt));
        }
        return activityMapper.selectList(new LambdaQueryWrapper<Activity>()
                .eq(Activity::getOrganizerId, UserContext.requireId())
                .orderByDesc(Activity::getCreatedAt));
    }

    public List<ActivitySession> sessionsOf(Long activityId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ActivitySession>()
                .eq(ActivitySession::getActivityId, activityId)
                .orderByAsc(ActivitySession::getStartTime));
    }

    /** 扫码核销：SETNX 防重复提交 + DB CAS（status VALID→USED）保证只核销一次 */
    public TicketVO checkIn(CheckInReq req) {
        UserContext.requireRole(LoginUser.ROLE_ORGANIZER, LoginUser.ROLE_ADMIN);
        Ticket t = ticketMapper.selectOne(new LambdaQueryWrapper<Ticket>()
                .eq(Ticket::getTicketNo, req.getTicketNo()));
        if (t == null) {
            throw new BizException(ResultCode.NOT_FOUND, "票不存在");
        }
        Activity a = activityMapper.selectById(t.getActivityId());
        if (a == null || (!a.getOrganizerId().equals(UserContext.requireId())
                && !LoginUser.ROLE_ADMIN.equals(UserContext.require().getRole()))) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        String lockKey = "lock:ticket:" + req.getTicketNo();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST, "核销请求处理中，请勿重复提交");
        }
        try {
            int rows = ticketMapper.checkIn(t.getId(), UserContext.requireId());
            if (rows == 0) {
                throw new BizException(ResultCode.ORDER_STATE_ERROR, "票已核销或已失效");
            }
            ActivitySession s = sessionMapper.selectById(t.getSessionId());
            t.setStatus(Ticket.STATUS_USED);
            return TicketVO.from(t, a, s);
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }

    private Activity requireOwn(Long id) {
        UserContext.requireRole(LoginUser.ROLE_ORGANIZER, LoginUser.ROLE_ADMIN);
        Activity a = activityMapper.selectById(id);
        if (a == null) {
            throw new BizException(ResultCode.NOT_FOUND, "活动不存在");
        }
        if (!a.getOrganizerId().equals(UserContext.requireId())
                && !LoginUser.ROLE_ADMIN.equals(UserContext.require().getRole())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return a;
    }

    private void evict(Long id) {
        stringRedisTemplate.delete(CACHE_KEY + id);
    }
}
