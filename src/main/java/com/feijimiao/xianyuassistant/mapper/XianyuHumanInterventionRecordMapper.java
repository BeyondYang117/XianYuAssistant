package com.feijimiao.xianyuassistant.mapper;

import com.feijimiao.xianyuassistant.entity.XianyuHumanInterventionRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface XianyuHumanInterventionRecordMapper {

    @Insert("INSERT INTO xianyu_human_intervention_record (xianyu_account_id, xy_goods_id, s_id, end_time) " +
            "VALUES (#{xianyuAccountId}, #{xyGoodsId}, #{sId}, #{endTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(XianyuHumanInterventionRecord record);

    @Select("SELECT * FROM xianyu_human_intervention_record WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} AND end_time > datetime('now', 'localtime') ORDER BY end_time DESC LIMIT 1")
    XianyuHumanInterventionRecord findActive(@Param("accountId") Long accountId, @Param("sId") String sId);

    @Delete("DELETE FROM xianyu_human_intervention_record WHERE xianyu_account_id = #{accountId} AND s_id = #{sId}")
    int deleteByConversation(@Param("accountId") Long accountId, @Param("sId") String sId);

    @Delete("DELETE FROM xianyu_human_intervention_record WHERE xianyu_account_id = #{accountId} AND s_id = #{sId} AND id <> #{keepId}")
    int deleteExpiredOrOlder(@Param("accountId") Long accountId,
                             @Param("sId") String sId,
                             @Param("keepId") Long keepId);

    @Delete("DELETE FROM xianyu_human_intervention_record WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);

    @Delete("DELETE FROM xianyu_human_intervention_record WHERE end_time < datetime('now', 'localtime')")
    int cleanExpired();
}
