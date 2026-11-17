package com.chh.campustix.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterReq {

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9_]{4,32}$", message = "用户名需为 4-32 位字母/数字/下划线")
    private String username;

    @NotBlank
    @Size(min = 6, max = 32)
    private String password;

    @Size(max = 32)
    private String nickname;

    @Size(max = 20)
    private String phone;
}
