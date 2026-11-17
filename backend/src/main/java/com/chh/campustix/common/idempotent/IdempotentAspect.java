package com.chh.campustix.common.idempotent;

import com.chh.campustix.common.api.ResultCode;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.security.LoginUser;
import com.chh.campustix.common.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;

    @Before("@annotation(idempotent)")
    public void check(JoinPoint jp, Idempotent idempotent) {
        String key = "idem:aop:" + idempotent.scene() + ":" + currentIdentity();
        Boolean first = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(idempotent.ttlSeconds()));
        if (Boolean.FALSE.equals(first)) {
            throw new BizException(ResultCode.DUPLICATE_REQUEST);
        }
    }

    private String currentIdentity() {
        LoginUser user = UserContext.get();
        if (user != null) {
            return "u" + user.id();
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? "ip" + attrs.getRequest().getRemoteAddr() : "unknown";
    }
}
