package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 账号自动任务配置Mapper
 */
@Mapper
public interface XianyuAccountTaskSettingMapper extends BaseMapper<XianyuAccountTaskSetting> {

    /**
     * 查询所有开启了自动擦亮的账号配置
     */
    @Select("SELECT * FROM xianyu_account_task_setting WHERE auto_polish_on = 1")
    List<XianyuAccountTaskSetting> selectPolishEnabled();

    /**
     * 查询所有开启了自动好评的账号配置
     */
    @Select("SELECT * FROM xianyu_account_task_setting WHERE auto_rate_on = 1")
    List<XianyuAccountTaskSetting> selectRateEnabled();

    /**
     * 保存或更新擦亮配置；账号维度只有一行，冲突时覆盖开关与时间点
     */
    @Insert("INSERT INTO xianyu_account_task_setting " +
            "(xianyu_account_id, auto_polish_on, polish_time, created_time, updated_time) " +
            "VALUES (#{accountId}, #{autoPolishOn}, #{polishTime}, " +
            "datetime('now', 'localtime'), datetime('now', 'localtime')) " +
            "ON CONFLICT(xianyu_account_id) DO UPDATE SET " +
            "auto_polish_on = excluded.auto_polish_on, " +
            "polish_time = excluded.polish_time, " +
            "updated_time = datetime('now', 'localtime')")
    int upsertPolishConfig(@Param("accountId") Long accountId,
                           @Param("autoPolishOn") Integer autoPolishOn,
                           @Param("polishTime") String polishTime);

    /**
     * 记录本日擦亮已执行。只有日期真正推进才更新，避免同一天重复扫描把时间戳来回改写
     */
    @Update("UPDATE xianyu_account_task_setting " +
            "SET last_polish_date = #{polishDate}, last_polish_at = #{polishAt}, " +
            "updated_time = datetime('now', 'localtime') " +
            "WHERE xianyu_account_id = #{accountId} AND last_polish_date <> #{polishDate}")
    int markPolishDone(@Param("accountId") Long accountId,
                       @Param("polishDate") String polishDate,
                       @Param("polishAt") long polishAt);

    /**
     * 保存或更新自动好评配置
     */
    @Insert("INSERT INTO xianyu_account_task_setting " +
            "(xianyu_account_id, auto_rate_on, rate_content, created_time, updated_time) " +
            "VALUES (#{accountId}, #{autoRateOn}, #{rateContent}, " +
            "datetime('now', 'localtime'), datetime('now', 'localtime')) " +
            "ON CONFLICT(xianyu_account_id) DO UPDATE SET " +
            "auto_rate_on = excluded.auto_rate_on, " +
            "rate_content = excluded.rate_content, " +
            "updated_time = datetime('now', 'localtime')")
    int upsertRateConfig(@Param("accountId") Long accountId,
                         @Param("autoRateOn") Integer autoRateOn,
                         @Param("rateContent") String rateContent);

    /**
     * 记录待评价扫描时间，供界面展示任务活跃度
     */
    @Update("UPDATE xianyu_account_task_setting SET last_rate_scan_at = #{scanAt}, " +
            "updated_time = datetime('now', 'localtime') WHERE xianyu_account_id = #{accountId}")
    int markRateScan(@Param("accountId") Long accountId, @Param("scanAt") long scanAt);

    @Delete("DELETE FROM xianyu_account_task_setting WHERE xianyu_account_id = #{accountId}")
    int deleteByAccountId(@Param("accountId") Long accountId);
}
