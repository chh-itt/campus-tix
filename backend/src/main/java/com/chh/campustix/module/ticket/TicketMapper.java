package com.chh.campustix.module.ticket;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {

    /** 核销 CAS：只有 VALID 才能变成 USED（防重复核销），返回影响行数 */
    @Update("UPDATE t_ticket SET status = 'USED', checked_in_at = NOW(3), checked_by = #{checkerId} " +
            "WHERE id = #{ticketId} AND status = 'VALID'")
    int checkIn(@Param("ticketId") Long ticketId, @Param("checkerId") Long checkerId);
}
