package com.chh.campustix.module.payment;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.props.CampustixProps;
import com.chh.campustix.module.order.Order;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 支付宝沙箱支付（campustix.alipay.enabled=true 时装配）。
 * 页面支付 pageExecute 返回自动提交表单 HTML；异步回调 rsaCheckV1 验签 + PaymentService 幂等处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campustix.alipay.enabled", havingValue = "true")
public class AlipayService {

    private final CampustixProps props;
    private AlipayClient client;

    @PostConstruct
    public void init() {
        client = new DefaultAlipayClient(
                props.getAlipay().getGateway(),
                props.getAlipay().getAppId(),
                props.getAlipay().getAppPrivateKey(),
                "json",
                "UTF-8",
                props.getAlipay().getAlipayPublicKey(),
                "RSA2");
        log.info("支付宝沙箱客户端已初始化 appId={}", props.getAlipay().getAppId());
    }

    /** 生成支付宝收银台页面（自动提交表单 HTML，前端 document.write 渲染） */
    public String createPayForm(Order order, String notifyUrl, String returnUrl) {
        try {
            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setNotifyUrl(notifyUrl);
            request.setReturnUrl(returnUrl);
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(order.getOrderNo());
            model.setTotalAmount(order.getTotalAmount().toPlainString());
            model.setSubject("CampusTix 活动票务订单 " + order.getOrderNo());
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            request.setBizModel(model);
            AlipayTradePagePayResponse resp = client.pageExecute(request);
            if (resp == null || resp.getBody() == null) {
                throw new BizException(ResultCode.ERROR, "支付宝下单失败");
            }
            return resp.getBody();
        } catch (AlipayApiException e) {
            log.error("支付宝下单失败 orderNo={}", order.getOrderNo(), e);
            throw new BizException(ResultCode.ERROR, "支付宝下单失败");
        }
    }

    public boolean verifyNotify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(params,
                    props.getAlipay().getAlipayPublicKey(), "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            log.warn("支付宝验签异常", e);
            return false;
        }
    }
}
