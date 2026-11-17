package com.chh.campustix.module.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderCreateReq {

    @NotNull
    private Long sessionId;

    @NotNull
    @Min(1)
    @Max(4)
    private Integer quantity;

    /** 前端下单前先 GET /api/orders/token 领取，提交时携带 */
    @NotBlank
    private String idempotencyToken;
}
