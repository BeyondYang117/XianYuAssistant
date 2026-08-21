package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 账号自动任务执行记录
 * run_key 唯一约束提供跨重启的幂等抢占，避免同一目标被重复执行
 */
@Data
@TableName("xianyu_account_task_run")
public class XianyuAccountTaskRun {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 幂等键，形如 polish:{accountId}:{itemId}:{yyyy-MM-dd}
     */
    private String runKey;

    private Long xianyuAccountId;

    /**
     * 任务类型 auto_polish / auto_rate
     */
    private String taskType;

    /**
     * 目标ID：商品ID或订单ID
     */
    private String targetId;

    /**
     * running / success / failed / needs_review
     */
    private String status;

    private Integer successCount;

    private Integer failedCount;

    private String errorMessage;

    private Long startedAt;

    private Long finishedAt;
}
