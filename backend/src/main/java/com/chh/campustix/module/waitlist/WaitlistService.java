package com.chh.campustix.module.waitlist;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.event.StockReleasedEvent;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.props.CampustixProps;
import com.chh.campustix.common.security.UserContext;
import com.chh.campustix.module.activity.ActivitySession;
import com.chh.campustix.module.activity.ActivitySessionMapper;
import com.chh.campustix.module.notice.NoticeService;
import com.chh.campustix.module.order.Order;
import com.chh.campustix.module.order.OrderService;
import com.chh.campustix.module.waitlist.dto.WaitlistVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 候补：售罄时排队；订单取消/超时释放库存后（StockReleasedEvent）自动唤醒队首，
 * 直接创建限时支付订单（CAS 抢占候补记录防并发重复唤醒）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistMapper waitlistMapper;
    private final ActivitySessionMapper sessionMapper;
    private final OrderService orderService;
    private final NoticeService noticeService;
    private final CampustixProps props;

    @Autowired
    @Lazy
    private WaitlistService self;

    public void join(Long sessionId) {
        Long userId = UserContext.requireId();
        ActivitySession s = sessionMapper.selectById(sessionId);
        if (s == null || !ActivitySession.STATUS_ON_SALE.equals(s.getStatus())) {
            throw new BizException(ResultCode.SALE_CLOSED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(s.getSaleEnd()) || now.isAfter(s.getStartTime())) {
            throw new BizException(ResultCode.SALE_CLOSED, "售票已结束，无法候补");
        }
        Long active = waitlistMapper.selectCount(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getSessionId, sessionId)
                .eq(Waitlist::getUserId, userId)
                .in(Waitlist::getStatus, Waitlist.STATUS_WAITING, Waitlist.STATUS_NOTIFIED));
        if (active != null && active > 0) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST, "您已在该场次候补队列中");
        }
        Waitlist w = new Waitlist();
        w.setSessionId(sessionId);
        w.setUserId(userId);
        w.setStatus(Waitlist.STATUS_WAITING);
        waitlistMapper.insert(w);
    }

    public void cancel(Long sessionId) {
        waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                .eq(Waitlist::getSessionId, sessionId)
                .eq(Waitlist::getUserId, UserContext.requireId())
                .eq(Waitlist::getStatus, Waitlist.STATUS_WAITING)
                .set(Waitlist::getStatus, Waitlist.STATUS_CANCELLED));
    }

    public List<WaitlistVO> mine() {
        List<Waitlist> list = waitlistMapper.selectList(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getUserId, UserContext.requireId())
                .orderByDesc(Waitlist::getCreatedAt)
                .last("LIMIT 100"));
        return list.stream()
                .map(w -> new WaitlistVO(w.getId(), w.getSessionId(), w.getStatus(), w.getNotifiedOrderNo(), w.getCreatedAt()))
                .toList();
    }

    /** 库存释放事件（订单取消/超时关单后触发）：唤醒队首，每人 1 张，单次最多 5 人 */
    @EventListener
    public void onStockReleased(StockReleasedEvent event) {
        int n = Math.min(event.quantity(), 5);
        for (int i = 0; i < n; i++) {
            try {
                if (!self.wakeOne(event.sessionId())) {
                    break;
                }
            } catch (Exception e) {
                log.warn("候补唤醒失败 sessionId={}", event.sessionId(), e);
                break;
            }
        }
    }

    /**
     * 唤醒一位：CAS 抢占候补记录（WAITING→NOTIFIED）→ 扣库存建限时单 → 通知。
     * @return true=继续尝试下一位；false=停止（无人候补/库存被抢/场次不可售）
     */
    @Transactional
    public boolean wakeOne(Long sessionId) {
        Waitlist head = waitlistMapper.selectOne(new LambdaQueryWrapper<Waitlist>()
                .eq(Waitlist::getSessionId, sessionId)
                .eq(Waitlist::getStatus, Waitlist.STATUS_WAITING)
                .orderByAsc(Waitlist::getCreatedAt)
                .last("LIMIT 1"));
        if (head == null) {
            return false;
        }
        // CAS 抢占，防止并发释放事件重复唤醒同一人
        int claimed = waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                .eq(Waitlist::getId, head.getId())
                .eq(Waitlist::getStatus, Waitlist.STATUS_WAITING)
                .set(Waitlist::getStatus, Waitlist.STATUS_NOTIFIED)
                .set(Waitlist::getNotifiedAt, LocalDateTime.now()));
        if (claimed == 0) {
            return true; // 被并发事件抢占，继续唤醒下一位
        }
        ActivitySession s = sessionMapper.selectById(sessionId);
        if (s == null || !ActivitySession.STATUS_ON_SALE.equals(s.getStatus())
                || LocalDateTime.now().isAfter(s.getSaleEnd())) {
            // 场次不可售：回退记录，终止唤醒
            waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                    .eq(Waitlist::getId, head.getId())
                    .eq(Waitlist::getStatus, Waitlist.STATUS_NOTIFIED)
                    .set(Waitlist::getStatus, Waitlist.STATUS_WAITING));
            return false;
        }
        Order order = orderService.createWaitlistOrder(head.getUserId(), s);
        if (order == null) {
            // 库存被正常购票者抢走：回退候补记录等待下一次释放
            waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                    .eq(Waitlist::getId, head.getId())
                    .eq(Waitlist::getStatus, Waitlist.STATUS_NOTIFIED)
                    .set(Waitlist::getStatus, Waitlist.STATUS_WAITING));
            return false;
        }
        waitlistMapper.update(null, new LambdaUpdateWrapper<Waitlist>()
                .eq(Waitlist::getId, head.getId())
                .set(Waitlist::getNotifiedOrderNo, order.getOrderNo()));
        noticeService.create(head.getUserId(), "候补成功",
                "您候补的名额已释放，请在 " + props.getOrder().getWaitlistPayTtlMinutes()
                        + " 分钟内完成支付，订单号：" + order.getOrderNo(), order.getOrderNo());
        return true;
    }
}
