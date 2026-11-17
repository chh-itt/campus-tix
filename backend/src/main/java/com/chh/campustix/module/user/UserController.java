package com.chh.campustix.module.user;

import com.chh.campustix.common.api.Result;
import com.chh.campustix.module.user.dto.LoginReq;
import com.chh.campustix.module.user.dto.LoginVO;
import com.chh.campustix.module.user.dto.RegisterReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(userService.register(req));
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(userService.login(req));
    }

    @GetMapping("/me")
    public Result<User> me() {
        return Result.ok(userService.me());
    }
}
