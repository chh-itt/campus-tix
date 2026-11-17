package com.chh.campustix.module.payment;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_payment")
public class Payment {

    public static final String STATUS_INIT = "INIT";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    public static final String CHANNEL_MOCK = "MOCK";
    public static final String CHANNEL_ALIPAY = "ALIPAY";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 商户单号 = 订单号 */
    private String outTradeNo;

    private String orderNo;

    /** MOCK / ALIPAY */
    private String channel;

    private BigDecimal amount;

    private String status;

    /** 渠道流水号 */
    private String tradeNo;

    private LocalDateTime callbackAt;

    private LocalDateTime refundedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
