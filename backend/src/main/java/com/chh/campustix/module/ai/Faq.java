package com.chh.campustix.module.ai;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_faq")
public class Faq {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** null 表示平台级 FAQ */
    private Long activityId;

    private String question;

    private String keywords;

    private String answer;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
