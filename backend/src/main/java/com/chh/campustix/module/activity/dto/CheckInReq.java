package com.chh.campustix.module.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckInReq {

    @NotBlank
    private String ticketNo;
}
