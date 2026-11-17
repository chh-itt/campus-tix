package com.chh.campustix.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 场次库存的 Redis 侧管理：
 * - 预扣（Lua 原子扣减）挡住绝大部分无效下单流量
 * - DB 条件更新（remaining >= N）是权威层，Redis 只是前置削峰
 * - key 不存在时由调用方从 DB 恢复
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockRedisService {

    public static final String KEY_PREFIX = "stock:session:";

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> stockDeductScript;
    private final DefaultRedisScript<Long> stockReturnScript;

    public static String key(Long sessionId) {
        return KEY_PREFIX + sessionId;
    }

    /** 发布/创建场次、启动兜底时初始化库存 key */
    public void init(Long sessionId, int remaining) {
        stringRedisTemplate.opsForValue().set(key(sessionId), String.valueOf(remaining), Duration.ofDays(7));
    }

    /** 仅在 key 不存在时初始化（启动自愈，避免覆盖真实库存） */
    public void initIfAbsent(Long sessionId, int remaining) {
        stringRedisTemplate.opsForValue().setIfAbsent(key(sessionId), String.valueOf(remaining), Duration.ofDays(7));
    }

    public DeductResult deduct(Long sessionId, int quantity) {
        Long r = stringRedisTemplate.execute(stockDeductScript,
                List.of(key(sessionId)), String.valueOf(quantity));
        if (r == null) {
            return DeductResult.MISS;
        }
        if (r == 1L) {
            return DeductResult.OK;
        }
        return r == 0L ? DeductResult.NOT_ENOUGH : DeductResult.MISS;
    }

    /** 取消/超时/退款后回补 */
    public boolean refund(Long sessionId, int quantity) {
        Long r = stringRedisTemplate.execute(stockReturnScript,
                List.of(key(sessionId)), String.valueOf(quantity));
        return r != null && r == 1L;
    }

    public Long get(Long sessionId) {
        String v = stringRedisTemplate.opsForValue().get(key(sessionId));
        try {
            return v == null ? null : Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 直接按 DB 权威值重置（自愈） */
    public void reset(Long sessionId, int remaining) {
        init(sessionId, remaining);
    }
}
