package com.chh.campustix.module.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityCreateReq {

    @NotBlank
    @Size(max = 64)
    private String title;

    @Size(max = 16)
    private String category;

    @Size(max = 255)
    private String cover;

    @Size(max = 2000)
    private String description;

    @Size(max = 64)
    private String venue;
}
