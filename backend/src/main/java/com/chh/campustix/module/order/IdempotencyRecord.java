package com.chh.campustix.module.order;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 业务幂等记录：token 唯一索引抢占，同一 token 重复提交返回原单 */
@Data
@TableName("t_idempotency_record")
public class IdempotencyRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String token;

    private String scene;

    private Long userId;

    /** 关联业务单号（订单号） */
    private String bizNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
