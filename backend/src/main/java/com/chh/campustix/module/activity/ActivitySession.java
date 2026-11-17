package com.chh.campustix.module.activity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 库存实体：名额/剩余挂在场次上 */
@Data
@TableName("t_activity_session")
public class ActivitySession {

    public static final String STATUS_ON_SALE = "ON_SALE";
    public static final String STATUS_OFF_SALE = "OFF_SALE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long activityId;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime saleStart;

    private LocalDateTime saleEnd;

    private BigDecimal price;

    private Integer quota;

    /** DB 层权威剩余名额 */
    private Integer remaining;

    private Integer sold;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
