package com.chh.campustix.module.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.event.StockReleasedEvent;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.mq.DelayQueue;
import com.chh.campustix.common.props.CampustixProps;
import com.chh.campustix.common.redis.DeductResult;
import com.chh.campustix.common.redis.StockRedisService;
import com.chh.campustix.common.security.UserContext;
import com.chh.campustix.module.activity.Activity;
import com.chh.campustix.module.activity.ActivityMapper;
import com.chh.campustix.module.activity.ActivitySession;
import com.chh.campustix.module.activity.ActivitySessionMapper;
import com.chh.campustix.module.order.dto.OrderCreateReq;
import com.chh.campustix.module.order.dto.OrderCreateVO;
import com.chh.campustix.module.order.dto.OrderPayVO;
import com.chh.campustix.module.order.dto.OrderVO;
import com.chh.campustix.module.payment.AlipayService;
import com.chh.campustix.module.payment.Payment;
import com.chh.campustix.module.payment.PaymentMapper;
import com.chh.campustix.common.util.SignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final ActivitySessionMapper sessionMapper;
    private final ActivityMapper activityMapper;
    private final PaymentMapper paymentMapper;
    private final StockRedisService stockRedis;
    private final IdempotencyService idempotencyService;
    private final DelayQueue delayQueue;
    private final CampustixProps props;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectProvider<AlipayService> alipayServiceProvider;

    /** 自注入代理：保证同类内部调用的 @Transactional 生效 */
    @Autowired
    @Lazy
    private OrderService self;

    // ==================== 下单 ====================

    /**
     * 下单主流程：幂等检查 → 场次校验 → Redis 预扣 → DB 条件扣减+落库（同事务）→ 绑定 token。
     * 任意失败：回滚 DB + 回补 Redis + 释放 token，保证两边一致。
     */
    public OrderCreateVO create(OrderCreateReq req) {
        Long userId = UserContext.requireId();
        String token = req.getIdempotencyToken();

        // 1) 幂等重放：同 token 已成功下单 → 返回原单
        String existing = idempotencyService.getBizNo(token, userId);
        if (existing != null) {
            Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, existing));
            if (o == null) {
                throw new BizException(ResultCode.NOT_FOUND, "原订单不存在");
            }
            return OrderCreateVO.from(o);
        }
        // 2) 占位：uk_token 唯一索引抢占，并发重复请求在此被拒
        if (!idempotencyService.tryInsert(token, userId)) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST, "请勿重复提交，请稍后在【我的订单】查看结果");
        }

        boolean redisDeducted = false;
        boolean bound = false;
        try {
            // 3) 场次可售校验
            ActivitySession session = sessionMapper.selectById(req.getSessionId());
            if (session == null) {
                throw new BizException(ResultCode.NOT_FOUND, "场次不存在");
            }
            if (!ActivitySession.STATUS_ON_SALE.equals(session.getStatus())) {
                throw new BizException(ResultCode.SALE_CLOSED);
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(session.getSaleStart()) || now.isAfter(session.getSaleEnd())
                    || now.isAfter(session.getStartTime())) {
                throw new BizException(ResultCode.SALE_CLOSED, "当前不在售票时间范围内");
            }

            // 4) Redis 预扣（Lua 原子扣减，挡住绝大多数无效流量）
            DeductResult dr = stockRedis.deduct(session.getId(), req.getQuantity());
            if (dr == DeductResult.MISS) {
                // key 不存在 → 用 DB 权威值自愈后重试一次
                stockRedis.initIfAbsent(session.getId(), session.getRemaining());
                dr = stockRedis.deduct(session.getId(), req.getQuantity());
            }
            if (dr == DeductResult.NOT_ENOUGH) {
                throw new BizException(ResultCode.SOLD_OUT);
            }
            redisDeducted = dr == DeductResult.OK;

            // 5) DB 条件扣减（remaining>=N 权威兜底）+ 订单落库 + 绑定 token，同一事务
            Order order = self.createInTx(userId, session, req.getQuantity(), token);
            redisDeducted = false;
            bound = true;
            return OrderCreateVO.from(order);
        } finally {
            if (redisDeducted) {
                stockRedis.refund(req.getSessionId(), req.getQuantity());
            }
            if (!bound) {
                idempotencyService.release(token, userId);
            }
        }
    }

    @Transactional
    public Order createInTx(Long userId, ActivitySession session, int quantity, String token) {
        // DB 层防超卖的权威保障：remaining >= quantity 才允许更新
        int rows = sessionMapper.deductStock(session.getId(), quantity);
        if (rows == 0) {
            throw new BizException(ResultCode.SOLD_OUT);
        }
        Order order = new Order();
        order.setId(IdWorker.getId());
        order.setOrderNo("CT" + order.getId());
        order.setUserId(userId);
        order.setActivityId(session.getActivityId());
        order.setSessionId(session.getId());
        order.setQuantity(quantity);
        order.setUnitPrice(session.getPrice());
        order.setTotalAmount(session.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(Order.STATUS_CREATED);
        order.setPayExpireAt(LocalDateTime.now().plusMinutes(props.getOrder().getPayTtlMinutes()));
        orderMapper.insert(order);

        // token 与订单号绑定（同事务：订单回滚则幂等记录也回滚）
        idempotencyService.bind(token, userId, order.getOrderNo());

        sendCloseAfterCommit(order.getOrderNo(), order.getPayExpireAt());
        return order;
    }

    /** 候补转正：直接为用户创建 1 张限时支付订单（库存被抢光返回 null） */
    @Transactional
    public Order createWaitlistOrder(Long userId, ActivitySession session) {
        int rows = sessionMapper.deductStock(session.getId(), 1);
        if (rows == 0) {
            return null;
        }
        Order order = new Order();
        order.setId(IdWorker.getId());
        order.setOrderNo("CT" + order.getId());
        order.setUserId(userId);
        order.setActivityId(session.getActivityId());
        order.setSessionId(session.getId());
        order.setQuantity(1);
        order.setUnitPrice(session.getPrice());
        order.setTotalAmount(session.getPrice());
        order.setStatus(Order.STATUS_CREATED);
        order.setPayExpireAt(LocalDateTime.now().plusMinutes(props.getOrder().getWaitlistPayTtlMinutes()));
        orderMapper.insert(order);
        sendCloseAfterCommit(order.getOrderNo(), order.getPayExpireAt());
        return order;
    }

    /** 事务提交后再投递延迟关单消息（防止消费端先于提交读到旧状态）；无事务时直接投递 */
    private void sendCloseAfterCommit(String orderNo, LocalDateTime expireAt) {
        long ttlSeconds = Math.max(Duration.between(LocalDateTime.now(), expireAt).getSeconds(), 1);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        delayQueue.sendCloseOrder(orderNo, ttlSeconds);
                    } catch (Exception e) {
                        log.warn("延迟关单消息投递失败（定时兜底会处理） orderNo={}", orderNo, e);
                    }
                }
            });
        } else {
            delayQueue.sendCloseOrder(orderNo, ttlSeconds);
        }
    }

    // ==================== 支付 ====================

    @Transactional
    public OrderPayVO pay(String orderNo, String alipayNotifyUrl, String alipayReturnUrl) {
        Order o = requireOwn(orderNo);
        if (Order.STATUS_PAID.equals(o.getStatus()) || Order.STATUS_CHECKED_IN.equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATE_ERROR, "订单已支付，请勿重复支付");
        }
        if (!Order.STATUS_CREATED.equals(o.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATE_ERROR, "订单已关闭，无法支付");
        }
        if (o.getPayExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.ORDER_STATE_ERROR, "订单已超时，请重新下单");
        }
        Payment p = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOutTradeNo, orderNo));
        if (p == null) {
            p = new Payment();
            p.setOutTradeNo(orderNo);
            p.setOrderNo(orderNo);
            p.setChannel(Payment.CHANNEL_MOCK);
            p.setAmount(o.getTotalAmount());
            p.setStatus(Payment.STATUS_INIT);
            paymentMapper.insert(p);
        }
        if (props.getAlipay().isEnabled()) {
            AlipayService alipay = alipayServiceProvider.getIfAvailable();
            if (alipay == null) {
                throw new BizException(ResultCode.PARAM_ERROR, "支付宝组件未就绪");
            }
            String form = alipay.createPayForm(o, alipayNotifyUrl, alipayReturnUrl);
            return new OrderPayVO("ALIPAY", null, form);
        }
        // 模拟网关：服务端生成带签名的收银台链接，签名不可伪造
        String tradeNo = "MOCK" + IdWorker.getId();
        String amountPlain = o.getTotalAmount().toPlainString();
        String sign = SignUtil.hmacSha256Hex(props.getMockPay().getSecret(),
                orderNo + "|" + amountPlain + "|" + tradeNo);
        String payUrl = "/api/payments/mock/pay?orderNo=" + orderNo
                + "&amount=" + amountPlain
                + "&tradeNo=" + tradeNo
                + "&sign=" + sign;
        return new OrderPayVO("MOCK", payUrl, null);
    }

    // ==================== 关单（取消/超时，三入口幂等收敛） ====================

    /** 用户取消 */
    public void cancel(String orderNo) {
        Order o = requireOwn(orderNo);
        self.close(o, Order.STATUS_CANCELLED, "USER_CANCEL");
    }

    /** 延迟消息触发：只有确实过期才关（消息早到/重复均安全） */
    public void closeIfExpiredByMessage(String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            return;
        }
        if (Order.STATUS_CREATED.equals(o.getStatus()) && o.getPayExpireAt().isBefore(LocalDateTime.now())) {
            self.close(o, Order.STATUS_EXPIRED, "PAY_EXPIRED");
        }
    }

    /** 定时兜底：每 30s 扫描过期未支付订单（消息丢失/消费失败的最终保障） */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void sweepExpiredOrders() {
        List<Order> expired = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, Order.STATUS_CREATED)
                .le(Order::getPayExpireAt, LocalDateTime.now())
                .last("LIMIT 100"));
        for (Order o : expired) {
            try {
                self.close(o, Order.STATUS_EXPIRED, "PAY_EXPIRED");
            } catch (Exception e) {
                log.warn("关单兜底失败 orderNo={}", o.getOrderNo(), e);
            }
        }
    }

    /**
     * CAS 关单：只有 CREATED → target 成功者才释放库存（消息/定时/用户三方并发只生效一次）。
     */
    @Transactional
    public void close(Order order, String targetStatus, String reason) {
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .eq(Order::getStatus, Order.STATUS_CREATED)
                .set(Order::getStatus, targetStatus)
                .set(Order::getCloseReason, reason));
        if (rows != 1) {
            return; // 已支付/已取消/已过期，CAS 失败即幂等收敛
        }
        sessionMapper.returnStock(order.getSessionId(), order.getQuantity());
        paymentMapper.update(null, new LambdaUpdateWrapper<Payment>()
                .eq(Payment::getOrderNo, order.getOrderNo())
                .eq(Payment::getStatus, Payment.STATUS_INIT)
                .set(Payment::getStatus, Payment.STATUS_FAILED));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    stockRedis.refund(order.getSessionId(), order.getQuantity());
                    eventPublisher.publishEvent(new StockReleasedEvent(order.getSessionId(), order.getQuantity()));
                }
            });
        } else {
            stockRedis.refund(order.getSessionId(), order.getQuantity());
            eventPublisher.publishEvent(new StockReleasedEvent(order.getSessionId(), order.getQuantity()));
        }
    }

    // ==================== 查询 ====================

    public List<OrderVO> listMine(long pageNo, long pageSize) {
        Long userId = UserContext.requireId();
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNo, Math.min(pageSize, 50)),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .orderByDesc(Order::getCreatedAt));
        return toVOs(page.getRecords());
    }

    public OrderVO detail(String orderNo) {
        Order o = requireOwn(orderNo);
        return toVOs(List.of(o)).get(0);
    }

    public Order requireOwn(String orderNo) {
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            throw new BizException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (!o.getUserId().equals(UserContext.requireId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return o;
    }

    private List<OrderVO> toVOs(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> activityIds = orders.stream().map(Order::getActivityId).collect(Collectors.toSet());
        Set<Long> sessionIds = orders.stream().map(Order::getSessionId).collect(Collectors.toSet());
        Map<Long, Activity> amap = activityMapper.selectBatchIds(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        Map<Long, ActivitySession> smap = sessionMapper.selectBatchIds(sessionIds).stream()
                .collect(Collectors.toMap(ActivitySession::getId, Function.identity()));
        return orders.stream()
                .map(o -> OrderVO.from(o, amap.get(o.getActivityId()), smap.get(o.getSessionId())))
                .collect(Collectors.toList());
    }
}
