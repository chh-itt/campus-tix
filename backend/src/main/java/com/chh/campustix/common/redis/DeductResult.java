package com.chh.campustix.common.redis;

public enum DeductResult {
    /** 扣减成功 */
    OK,
    /** 库存不足 */
    NOT_ENOUGH,
    /** 库存 key 不存在（未初始化或已过期），需要从 DB 恢复后重试 */
    MISS
}
