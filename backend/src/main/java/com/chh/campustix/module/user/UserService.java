package com.chh.campustix.module.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.security.JwtUtil;
import com.chh.campustix.common.security.LoginUser;
import com.chh.campustix.common.security.UserContext;
import com.chh.campustix.module.user.dto.LoginReq;
import com.chh.campustix.module.user.dto.LoginVO;
import com.chh.campustix.module.user.dto.RegisterReq;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginVO register(RegisterReq req) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST, "用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null ? req.getNickname() : req.getUsername());
        user.setPhone(req.getPhone());
        user.setRole(LoginUser.ROLE_STUDENT);
        user.setStatus(1);
        userMapper.insert(user);

        String token = jwtUtil.create(new LoginUser(user.getId(), user.getUsername(), user.getRole()));
        return new LoginVO(user.getId(), user.getUsername(), user.getRole(), token);
    }

    public LoginVO login(LoginReq req) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        String token = jwtUtil.create(new LoginUser(user.getId(), user.getUsername(), user.getRole()));
        return new LoginVO(user.getId(), user.getUsername(), user.getRole(), token);
    }

    public User me() {
        User user = userMapper.selectById(UserContext.requireId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }
}
