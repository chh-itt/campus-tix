package com.chh.campustix.module.activity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivitySessionMapper extends BaseMapper<ActivitySession> {

    /**
     * 条件扣减库存（防超卖的 DB 层权威保障）：只有 remaining >= quantity 才更新。
     * @return 影响行数：1 成功，0 库存不足
     */
    @Update("UPDATE t_activity_session SET remaining = remaining - #{quantity}, sold = sold + #{quantity} " +
            "WHERE id = #{sessionId} AND remaining >= #{quantity} AND status = 'ON_SALE'")
    int deductStock(@Param("sessionId") Long sessionId, @Param("quantity") int quantity);

    /** 回补库存（取消/超时/退款） */
    @Update("UPDATE t_activity_session SET remaining = remaining + #{quantity}, sold = sold - #{quantity} " +
            "WHERE id = #{sessionId}")
    int returnStock(@Param("sessionId") Long sessionId, @Param("quantity") int quantity);
}
