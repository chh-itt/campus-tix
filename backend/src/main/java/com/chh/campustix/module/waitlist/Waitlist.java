package com.chh.campustix.module.waitlist;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 候补队列：WAITING→NOTIFIED→FULFILLED/EXPIRED/CANCELLED */
@Data
@TableName("t_waitlist")
public class Waitlist {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_NOTIFIED = "NOTIFIED";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String status;

    /** 转正后的订单号 */
    private String notifiedOrderNo;

    private LocalDateTime notifiedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
