package com.chh.campustix.common.security;

import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;

import java.util.Arrays;

public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static LoginUser require() {
        LoginUser user = get();
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long requireId() {
        return require().id();
    }

    public static void requireRole(String... roles) {
        LoginUser user = require();
        boolean matched = Arrays.stream(roles).anyMatch(r -> r.equals(user.role()));
        if (!matched) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    public static void clear() {
        HOLDER.remove();
    }
}
