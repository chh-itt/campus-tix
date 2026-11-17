package com.chh.campustix.module.order;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单状态机：CREATED→PAID→CHECKED_IN；CREATED→CANCELLED/EXPIRED；PAID→REFUNDING→REFUNDED */
@Data
@TableName("t_order")
public class Order {

    public static final String STATUS_CREATED = "CREATED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CHECKED_IN = "CHECKED_IN";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_REFUNDING = "REFUNDING";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** "CT" + 雪花id */
    private String orderNo;

    private Long userId;

    private Long activityId;

    private Long sessionId;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal totalAmount;

    private String status;

    /** USER_CANCEL / PAY_EXPIRED / LATE_PAY_REFUND */
    private String closeReason;

    private LocalDateTime payExpireAt;

    private LocalDateTime paidAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
