package com.chh.campustix.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FaqAskReq {

    /** null 表示平台级问答 */
    private Long activityId;

    @NotBlank
    @Size(max = 200)
    private String question;
}
