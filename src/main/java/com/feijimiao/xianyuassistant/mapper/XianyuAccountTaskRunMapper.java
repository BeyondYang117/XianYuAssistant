package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskRun;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 账号自动任务执行记录Mapper
 */
@Mapper
public interface XianyuAccountTaskRunMapper extends BaseMapper<XianyuAccountTaskRun> {

    /**
     * 抢占一次任务执行。依赖 run_key 唯一索引：
     * 插入成功（返回1）表示本进程获得执行权；冲突时不覆盖既有记录，返回0表示应跳过。
     * 与发货租约不同，这里没有租期回收——擦亮/好评按天或按订单只该执行一次，
     * 崩溃残留的 running 记录宁可漏执行一轮，也不能重复调用平台接口。
     */
    @Insert("INSERT INTO xianyu_account_task_run " +
            "(run_key, xianyu_account_id, task_type, target_id, status, started_at) " +
            "VALUES (#{runKey}, #{accountId}, #{taskType}, #{targetId}, 'running', #{startedAt}) " +
            "ON CONFLICT(run_key) DO NOTHING")
    int tryClaim(@Param("runKey") String runKey,
                 @Param("accountId") Long accountId,
                 @Param("taskType") String taskType,
                 @Param("targetId") String targetId,
                 @Param("startedAt") long startedAt);

    /**
     * 收口任务结果
     */
    @Update("UPDATE xianyu_account_task_run SET status = #{status}, success_count = #{successCount}, " +
            "failed_count = #{failedCount}, error_message = #{errorMessage}, finished_at = #{finishedAt} " +
            "WHERE run_key = #{runKey}")
    int finish(@Param("runKey") String runKey,
               @Param("status") String status,
               @Param("successCount") int successCount,
               @Param("failedCount") int failedCount,
               @Param("errorMessage") String errorMessage,
               @Param("finishedAt") long finishedAt);

    /**
     * 查询账号最近的任务执行记录
     */
    @Select("SELECT * FROM xianyu_account_task_run WHERE xianyu_account_id = #{accountId} " +
            "AND task_type = #{taskType} ORDER BY started_at DESC LIMIT #{limit}")
    List<XianyuAccountTaskRun> selectRecent(@Param("accountId") Long accountId,
                                            @Param("taskType") String taskType,
                                            @Param("limit") int limit);

    /**
     * 清理指定类型的历史记录，避免执行记录无限增长。
     * 只能用于 run_key 含日期的任务（如擦亮）：这类 key 每天都不同，删除旧记录不会破坏幂等。
     * 好评的 run_key 不含日期，是永久幂等依据，删除会导致老订单被重复评价，因此必须按 task_type 精确限定。
     */
    @Delete("DELETE FROM xianyu_account_task_run WHERE task_type = #{taskType} AND started_at < #{before}")
    int deleteByTypeBefore(@Param("taskType") String taskType, @Param("before") long before);

    @Delete("DELETE FROM xianyu_account_task_run WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
