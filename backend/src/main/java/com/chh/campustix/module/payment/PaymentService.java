package com.chh.campustix.module.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chh.campustix.module.notice.NoticeService;
import com.chh.campustix.module.order.Order;
import com.chh.campustix.module.order.OrderMapper;
import com.chh.campustix.module.ticket.Ticket;
import com.chh.campustix.module.ticket.TicketMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付成功回调统一入口（MOCK/ALIPAY 共用）。
 * 幂等设计：重复回调直接返回成功；金额不符拒绝；订单已关闭时走"迟到支付→自动退款"状态机。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final NoticeService noticeService;

    @Transactional
    public boolean onPaySuccess(String orderNo, String channel, String tradeNo, BigDecimal amount) {
        Payment p = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOutTradeNo, orderNo));
        if (p == null) {
            log.warn("支付回调无支付单 orderNo={}", orderNo);
            return false;
        }
        if (amount != null && p.getAmount().compareTo(amount) != 0) {
            log.warn("支付回调金额不符 orderNo={} expect={} actual={}", orderNo, p.getAmount(), amount);
            return false;
        }
        Order o = orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (o == null) {
            log.warn("支付回调无订单 orderNo={}", orderNo);
            return false;
        }
        // 幂等：该支付单已处理过成功回调
        if (Payment.STATUS_SUCCESS.equals(p.getStatus()) || Payment.STATUS_REFUNDED.equals(p.getStatus())) {
            return true;
        }

        // 正常路径：CREATED → PAID → 出票
        if (Order.STATUS_CREATED.equals(o.getStatus())) {
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, o.getId())
                    .eq(Order::getStatus, Order.STATUS_CREATED)
                    .set(Order::getStatus, Order.STATUS_PAID)
                    .set(Order::getPaidAt, LocalDateTime.now()));
            p.setStatus(Payment.STATUS_SUCCESS);
            p.setChannel(channel);
            p.setTradeNo(tradeNo);
            p.setCallbackAt(LocalDateTime.now());
            paymentMapper.updateById(p);
            if (rows == 1) {
                for (int i = 0; i < o.getQuantity(); i++) {
                    Ticket t = new Ticket();
                    t.setId(IdWorker.getId());
                    t.setTicketNo("CT" + t.getId());
                    t.setOrderId(o.getId());
                    t.setOrderNo(o.getOrderNo());
                    t.setActivityId(o.getActivityId());
                    t.setSessionId(o.getSessionId());
                    t.setUserId(o.getUserId());
                    t.setStatus(Ticket.STATUS_VALID);
                    ticketMapper.insert(t);
                }
                noticeService.create(o.getUserId(), "报名成功",
                        "订单 " + o.getOrderNo() + " 支付成功，票已生成，请在【我的票】查看核销码。", o.getOrderNo());
            }
            // rows==0：并发回调另一线程已完成，按幂等成功处理
            return true;
        }

        // 迟到支付：钱已收但订单已关（EXPIRED/CANCELLED）→ 自动退款状态机
        if (Order.STATUS_EXPIRED.equals(o.getStatus()) || Order.STATUS_CANCELLED.equals(o.getStatus())) {
            p.setStatus(Payment.STATUS_SUCCESS);
            p.setChannel(channel);
            p.setTradeNo(tradeNo);
            p.setCallbackAt(LocalDateTime.now());
            paymentMapper.updateById(p);
            int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getId, o.getId())
                    .in(Order::getStatus, Order.STATUS_EXPIRED, Order.STATUS_CANCELLED)
                    .set(Order::getStatus, Order.STATUS_REFUNDING)
                    .set(Order::getCloseReason, "LATE_PAY_REFUND"));
            if (rows == 1) {
                orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, o.getId())
                        .eq(Order::getStatus, Order.STATUS_REFUNDING)
                        .set(Order::getStatus, Order.STATUS_REFUNDED));
                p.setStatus(Payment.STATUS_REFUNDED);
                p.setRefundedAt(LocalDateTime.now());
                paymentMapper.updateById(p);
                noticeService.create(o.getUserId(), "订单退款",
                        "订单 " + o.getOrderNo() + " 已关闭后收到付款，款项将原路退回。", o.getOrderNo());
            }
            return true;
        }

        // 其他状态（PAID/CHECKED_IN）：补记回调流水即可
        if (Payment.STATUS_INIT.equals(p.getStatus()) || Payment.STATUS_FAILED.equals(p.getStatus())) {
            p.setStatus(Payment.STATUS_SUCCESS);
            p.setChannel(channel);
            p.setTradeNo(tradeNo);
            p.setCallbackAt(LocalDateTime.now());
            paymentMapper.updateById(p);
        }
        return true;
    }
}
