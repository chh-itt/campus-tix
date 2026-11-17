package com.chh.campustix.module.ticket;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ticket")
public class Ticket {

    public static final String STATUS_VALID = "VALID";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_VOID = "VOID";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 核销码/票号："CT" + 雪花id（二维码内容） */
    private String ticketNo;

    private Long orderId;

    private String orderNo;

    private Long activityId;

    private Long sessionId;

    private Long userId;

    private String status;

    private LocalDateTime checkedInAt;

    private Long checkedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
