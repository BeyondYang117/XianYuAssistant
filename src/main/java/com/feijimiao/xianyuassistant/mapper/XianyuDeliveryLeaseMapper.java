package com.feijimiao.xianyuassistant.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface XianyuDeliveryLeaseMapper {

    @Insert("INSERT INTO xianyu_delivery_lease (xianyu_account_id, order_id, status, lease_until, updated_time) " +
            "VALUES (#{accountId}, #{orderId}, 0, #{leaseUntil}, datetime('now', 'localtime')) " +
            "ON CONFLICT(xianyu_account_id, order_id) DO UPDATE SET status = 0, lease_until = excluded.lease_until, " +
            "updated_time = datetime('now', 'localtime') " +
            "WHERE xianyu_delivery_lease.status <> 1 AND xianyu_delivery_lease.lease_until < #{now}")
    int tryAcquire(@Param("accountId") Long accountId,
                   @Param("orderId") String orderId,
                   @Param("now") long now,
                   @Param("leaseUntil") long leaseUntil);

    @Update("UPDATE xianyu_delivery_lease SET status = 1, lease_until = 0, updated_time = datetime('now', 'localtime') " +
            "WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} AND status = 0")
    int markSuccess(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Delete("DELETE FROM xianyu_delivery_lease WHERE xianyu_account_id = #{accountId} AND order_id = #{orderId} AND status = 0")
    int release(@Param("accountId") Long accountId, @Param("orderId") String orderId);

    @Delete("DELETE FROM xianyu_delivery_lease WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
