package com.chh.campustix.common.idempotent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 通用防重复提交：以 scene + 用户(或IP) 为粒度做 Redis SETNX 短窗口幂等。
 * 订单创建等强幂等场景另走 token + DB 唯一索引双重保障。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    String scene();

    int ttlSeconds() default 10;
}
