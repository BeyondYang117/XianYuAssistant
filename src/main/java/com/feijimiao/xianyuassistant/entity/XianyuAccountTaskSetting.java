package com.feijimiao.xianyuassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 账号级自动任务配置
 * 每个闲鱼账号一行，承载每日擦亮等按账号开关的定时任务参数
 */
@Data
@TableName("xianyu_account_task_setting")
public class XianyuAccountTaskSetting {

    /**
     * 闲鱼账号ID，同时作为主键
     */
    @TableId(value = "xianyu_account_id", type = IdType.INPUT)
    private Long xianyuAccountId;

    /**
     * 是否开启每日自动擦亮 0:关闭 1:开启
     */
    private Integer autoPolishOn;

    /**
     * 擦亮触发时刻，北京时间 HH:mm
     */
    private String polishTime;

    /**
     * 最近成功擦亮的日期 yyyy-MM-dd，当日幂等依据
     */
    private String lastPolishDate;

    /**
     * 最近一次擦亮执行时间戳（毫秒）
     */
    private Long lastPolishAt;

    /**
     * 是否开启自动好评买家 0:关闭 1:开启
     */
    private Integer autoRateOn;

    /**
     * 好评内容
     */
    private String rateContent;

    /**
     * 最近一次待评价扫描时间戳（毫秒）
     */
    private Long lastRateScanAt;

    /**
     * 是否开启超时求评价 0:关闭 1:开启
     */
    private Integer reviewRequestOn;

    /**
     * 求评价话术
     */
    private String reviewRequestContent;

    /**
     * 发货后多少小时首次求评价
     */
    private Integer reviewRequestDelayHours;

    /**
     * 再次求评价的间隔小时数
     */
    private Integer reviewRequestIntervalHours;

    /**
     * 最多求评价次数
     */
    private Integer reviewRequestMaxAttempts;

    private String createdTime;

    private String updatedTime;
}
