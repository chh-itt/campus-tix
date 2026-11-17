package com.chh.campustix.module.payment;

import com.chh.campustix.common.props.CampustixProps;
import com.chh.campustix.common.util.SignUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付网关侧入口：
 * - /mock/pay 模拟网关收银台（服务端签名链接，用户点击确认支付）
 * - /mock/notify 模拟网关异步回调
 * - /alipay/notify 支付宝沙箱异步回调（验签 + 幂等 + success/failure 应答）
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String CASHIER_TEMPLATE = """
            <html lang="zh"><head><meta charset="utf-8"><title>CampusTix 模拟收银台</title>
            <style>
              body{font-family:system-ui,-apple-system,sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;background:#f5f6fa;margin:0}
              .card{background:#fff;border-radius:12px;padding:40px 56px;box-shadow:0 8px 30px rgba(0,0,0,.08);text-align:center}
              .no{color:#888;font-size:13px}
              .amt{font-size:38px;color:#e63946;font-weight:700;margin:10px 0 26px}
              button{background:#1677ff;color:#fff;border:none;border-radius:8px;padding:12px 56px;font-size:16px;cursor:pointer}
              button:hover{background:#0958d9}
            </style></head><body>
            <div class="card">
              <h2>CampusTix 模拟收银台</h2>
              <div class="no">订单号：%s</div>
              <div class="amt">&yen;%s</div>
              <form method="post" action="/api/payments/mock/notify">
                <input type="hidden" name="orderNo" value="%s">
                <input type="hidden" name="amount" value="%s">
                <input type="hidden" name="tradeNo" value="%s">
                <input type="hidden" name="sign" value="%s">
                <button type="submit">确认支付</button>
              </form>
            </div></body></html>""";

    private final PaymentService paymentService;
    private final CampustixProps props;
    private final ObjectProvider<AlipayService> alipayServiceProvider;

    @GetMapping(value = "/mock/pay", produces = "text/html;charset=UTF-8")
    public String mockPay(@RequestParam String orderNo, @RequestParam String amount,
                          @RequestParam String tradeNo, @RequestParam String sign) {
        String expect = SignUtil.hmacSha256Hex(props.getMockPay().getSecret(),
                orderNo + "|" + amount + "|" + tradeNo);
        if (!expect.equals(sign)) {
            return simplePage("非法支付请求", "签名校验失败");
        }
        return CASHIER_TEMPLATE.formatted(orderNo, amount, orderNo, amount, tradeNo, sign);
    }

    @PostMapping(value = "/mock/notify", produces = "text/html;charset=UTF-8")
    public String mockNotify(@RequestParam String orderNo, @RequestParam String amount,
                             @RequestParam String tradeNo, @RequestParam String sign) {
        String expect = SignUtil.hmacSha256Hex(props.getMockPay().getSecret(),
                orderNo + "|" + amount + "|" + tradeNo);
        if (!expect.equals(sign)) {
            return simplePage("支付失败", "签名校验失败");
        }
        boolean ok;
        try {
            ok = paymentService.onPaySuccess(orderNo, Payment.CHANNEL_MOCK, tradeNo, new BigDecimal(amount));
        } catch (Exception e) {
            log.error("模拟支付回调处理异常 orderNo={}", orderNo, e);
            ok = false;
        }
        return ok
                ? simplePage("支付成功", "票已生成，请返回页面查看【我的票】核销码。")
                : simplePage("支付失败", "订单处理失败，请联系主办方。");
    }

    /** 支付宝异步通知：验签 + 处理，应答纯文本 success / failure（failure 会让支付宝重试） */
    @PostMapping(value = "/alipay/notify", produces = "text/plain;charset=UTF-8")
    public String alipayNotify(HttpServletRequest request) {
        AlipayService alipay = alipayServiceProvider.getIfAvailable();
        if (alipay == null) {
            return "failure";
        }
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, String.join(",", v)));
        if (!alipay.verifyNotify(params)) {
            log.warn("支付宝回调验签失败 out_trade_no={}", params.get("out_trade_no"));
            return "failure";
        }
        String tradeStatus = params.get("trade_status");
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            try {
                paymentService.onPaySuccess(params.get("out_trade_no"), Payment.CHANNEL_ALIPAY,
                        params.get("trade_no"), new BigDecimal(params.get("total_amount")));
            } catch (Exception e) {
                log.error("支付宝回调处理异常 out_trade_no={}", params.get("out_trade_no"), e);
                return "failure";
            }
        }
        return "success";
    }

    private String simplePage(String title, String msg) {
        return "<html lang=\"zh\"><head><meta charset=\"utf-8\"><title>" + title
                + "</title></head><body style=\"font-family:system-ui;text-align:center;padding-top:80px\">"
                + "<h2>" + title + "</h2><p>" + msg + "</p>"
                + "<p><a href=\"javascript:history.back()\">返回</a></p></body></html>";
    }
}
