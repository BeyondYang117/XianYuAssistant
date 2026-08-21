package com.feijimiao.xianyuassistant.mapper;

import com.feijimiao.xianyuassistant.entity.XianyuAutoReplyDelayTask;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface XianyuAutoReplyDelayTaskMapper {

    @Insert("INSERT INTO xianyu_auto_reply_delay_task (task_key, xianyu_account_id, s_id, messages_json, execute_at, updated_time) " +
            "VALUES (#{taskKey}, #{xianyuAccountId}, #{sId}, #{messagesJson}, #{executeAt}, datetime('now', 'localtime')) " +
            "ON CONFLICT(task_key) DO UPDATE SET xianyu_account_id = excluded.xianyu_account_id, " +
            "s_id = excluded.s_id, messages_json = excluded.messages_json, execute_at = excluded.execute_at, " +
            "updated_time = datetime('now', 'localtime')")
    int upsert(XianyuAutoReplyDelayTask task);

    @Select("SELECT * FROM xianyu_auto_reply_delay_task ORDER BY execute_at")
    List<XianyuAutoReplyDelayTask> selectAll();

    @Delete("DELETE FROM xianyu_auto_reply_delay_task WHERE task_key = #{taskKey}")
    int deleteByTaskKey(@Param("taskKey") String taskKey);

    @Delete("DELETE FROM xianyu_auto_reply_delay_task WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT COUNT(*) FROM xianyu_auto_reply_delay_task WHERE task_key = #{taskKey} AND execute_at = #{executeAt}")
    int isCurrent(@Param("taskKey") String taskKey, @Param("executeAt") long executeAt);

    @Delete("DELETE FROM xianyu_auto_reply_delay_task WHERE task_key = #{taskKey} AND execute_at = #{executeAt}")
    int deleteVersion(@Param("taskKey") String taskKey, @Param("executeAt") long executeAt);
}
