package com.chh.campustix.module.order.dto;

/** 支付发起结果：MOCK 返回 payUrl（收银台页面）；ALIPAY 返回 payForm（自动提交表单 HTML） */
public record OrderPayVO(String payType, String payUrl, String payForm) {
}
