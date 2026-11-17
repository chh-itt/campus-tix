package com.chh.campustix.module.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SessionCreateReq {

    @NotBlank
    @Size(max = 32)
    private String title;

    @NotNull
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime saleStart;

    @NotNull
    private LocalDateTime saleEnd;

    @NotNull
    @Min(0)
    private BigDecimal price;

    @NotNull
    @Min(1)
    @Max(100000)
    private Integer quota;
}
