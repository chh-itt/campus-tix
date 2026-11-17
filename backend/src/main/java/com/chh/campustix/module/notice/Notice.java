package com.chh.campustix.module.notice;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知（表无 updated_at，不声明该字段，MetaObjectHandler 严格填充会自动跳过） */
@Data
@TableName("t_notice")
public class Notice {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    private Integer isRead;

    private String bizNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
