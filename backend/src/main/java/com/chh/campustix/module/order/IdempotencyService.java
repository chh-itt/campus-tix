package com.chh.campustix.module.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chh.campustix.common.exception.BizException;
import com.chh.campustix.common.api.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 业务级幂等（token + DB 唯一索引）：
 * 1) getBizNo：同 token 已成功下单 → 直接返回原单（重放安全）
 * 2) tryInsert：uk_token 抢占，并发重复请求被拒绝
 * 3) bind：下单事务内绑定订单号；release：失败后释放未绑定记录允许重试
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordMapper recordMapper;

    public String getBizNo(String token, Long userId) {
        IdempotencyRecord r = recordMapper.selectOne(new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getToken, token)
                .eq(IdempotencyRecord::getUserId, userId));
        return r == null ? null : r.getBizNo();
    }

    public boolean tryInsert(String token, Long userId) {
        try {
            IdempotencyRecord r = new IdempotencyRecord();
            r.setToken(token);
            r.setScene("ORDER_CREATE");
            r.setUserId(userId);
            recordMapper.insert(r);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void bind(String token, Long userId, String bizNo) {
        recordMapper.update(null, new LambdaUpdateWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getToken, token)
                .eq(IdempotencyRecord::getUserId, userId)
                .set(IdempotencyRecord::getBizNo, bizNo));
    }

    /** 只释放尚未绑定 bizNo 的记录（成功订单的 token 保留，支撑重放返回原单） */
    public void release(String token, Long userId) {
        recordMapper.delete(new LambdaQueryWrapper<IdempotencyRecord>()
                .eq(IdempotencyRecord::getToken, token)
                .eq(IdempotencyRecord::getUserId, userId)
                .isNull(IdempotencyRecord::getBizNo));
    }
}
