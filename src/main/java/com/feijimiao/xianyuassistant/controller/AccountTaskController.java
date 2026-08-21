package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskRun;
import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskRunMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskSettingMapper;
import com.feijimiao.xianyuassistant.service.AutoPolishService;
import com.feijimiao.xianyuassistant.service.AutoRateService;
import com.feijimiao.xianyuassistant.service.BuyerRateService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 账号自动任务控制器
 * 目前承载每日自动擦亮的配置、手动执行与执行记录查询
 */
@Slf4j
@RestController
@RequestMapping("/api/account-tasks")
@CrossOrigin(origins = "*")
public class AccountTaskController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private XianyuAccountTaskSettingMapper settingMapper;

    @Autowired
    private XianyuAccountTaskRunMapper runMapper;

    @Autowired
    private AutoPolishService autoPolishService;

    @Autowired
    private AutoRateService autoRateService;

    /**
     * 擦亮配置请求
     */
    @Data
    public static class PolishConfigReqDTO {
        private Long xianyuAccountId;
        private Integer autoPolishOn;
        /**
         * 擦亮时刻，北京时间 HH:mm
         */
        private String polishTime;
    }

    /**
     * 好评配置请求
     */
    @Data
    public static class RateConfigReqDTO {
        private Long xianyuAccountId;
        private Integer autoRateOn;
        /**
         * 好评内容
         */
        private String rateContent;
    }

    /**
     * 查询账号自动任务配置（擦亮 + 好评同在一行）
     */
    @GetMapping("/config")
    public ResultObject<XianyuAccountTaskSetting> getConfig(@RequestParam Long xianyuAccountId) {
        try {
            XianyuAccountTaskSetting setting = settingMapper.selectById(xianyuAccountId);
            if (setting == null) {
                // 未配置过的账号返回默认关闭状态，前端无需区分"无记录"和"已关闭"
                setting = new XianyuAccountTaskSetting();
                setting.setXianyuAccountId(xianyuAccountId);
                setting.setAutoPolishOn(0);
                setting.setPolishTime("03:00");
                setting.setLastPolishDate("");
                setting.setLastPolishAt(0L);
                setting.setAutoRateOn(0);
                setting.setRateContent(BuyerRateService.DEFAULT_RATE_CONTENT);
                setting.setLastRateScanAt(0L);
            }
            return ResultObject.success(setting);
        } catch (Exception e) {
            log.error("查询账号任务配置失败: accountId={}", xianyuAccountId, e);
            return ResultObject.failed("查询账号任务配置失败: " + e.getMessage());
        }
    }

    /**
     * 保存账号擦亮配置
     */
    @PostMapping("/polish/config")
    public ResultObject<?> savePolishConfig(@RequestBody PolishConfigReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return ResultObject.validateFailed("缺少账号ID");
        }

        String polishTime = normalizeTime(reqDTO.getPolishTime());
        if (polishTime == null) {
            return ResultObject.validateFailed("擦亮时间格式非法，应为 HH:mm");
        }

        try {
            int autoPolishOn = Integer.valueOf(1).equals(reqDTO.getAutoPolishOn()) ? 1 : 0;
            settingMapper.upsertPolishConfig(reqDTO.getXianyuAccountId(), autoPolishOn, polishTime);
            log.info("保存擦亮配置: accountId={}, on={}, time={}",
                    reqDTO.getXianyuAccountId(), autoPolishOn, polishTime);
            return ResultObject.success(null, "保存成功");
        } catch (Exception e) {
            log.error("保存擦亮配置失败: accountId={}", reqDTO.getXianyuAccountId(), e);
            return ResultObject.failed("保存擦亮配置失败: " + e.getMessage());
        }
    }

    /**
     * 立即擦亮该账号全部在售商品
     * 当天已擦亮过的商品会被幂等键跳过，重复点击不会重复调用平台
     */
    @PostMapping("/polish/run")
    public ResultObject<AutoPolishService.PolishSummary> polishNow(@RequestParam Long xianyuAccountId) {
        try {
            AutoPolishService.PolishSummary summary = autoPolishService.polishNow(xianyuAccountId);
            return ResultObject.success(summary, summary.message());
        } catch (Exception e) {
            log.error("手动擦亮失败: accountId={}", xianyuAccountId, e);
            return ResultObject.failed("手动擦亮失败: " + e.getMessage());
        }
    }

    /**
     * 查询擦亮执行记录
     */
    @GetMapping("/polish/runs")
    public ResultObject<List<XianyuAccountTaskRun>> polishRuns(@RequestParam Long xianyuAccountId,
                                                               @RequestParam(defaultValue = "50") int limit) {
        try {
            int effectiveLimit = Math.min(Math.max(limit, 1), 200);
            return ResultObject.success(
                    runMapper.selectRecent(xianyuAccountId, AutoPolishService.TASK_TYPE, effectiveLimit));
        } catch (Exception e) {
            log.error("查询擦亮记录失败: accountId={}", xianyuAccountId, e);
            return ResultObject.failed("查询擦亮记录失败: " + e.getMessage());
        }
    }

    /**
     * 保存账号好评配置
     */
    @PostMapping("/rate/config")
    public ResultObject<?> saveRateConfig(@RequestBody RateConfigReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return ResultObject.validateFailed("缺少账号ID");
        }

        String rateContent = reqDTO.getRateContent() == null ? "" : reqDTO.getRateContent().trim();
        if (rateContent.isEmpty()) {
            rateContent = BuyerRateService.DEFAULT_RATE_CONTENT;
        }
        if (rateContent.length() > 500) {
            return ResultObject.validateFailed("好评内容不能超过500字");
        }

        try {
            int autoRateOn = Integer.valueOf(1).equals(reqDTO.getAutoRateOn()) ? 1 : 0;
            settingMapper.upsertRateConfig(reqDTO.getXianyuAccountId(), autoRateOn, rateContent);
            log.info("保存好评配置: accountId={}, on={}", reqDTO.getXianyuAccountId(), autoRateOn);
            return ResultObject.success(null, "保存成功");
        } catch (Exception e) {
            log.error("保存好评配置失败: accountId={}", reqDTO.getXianyuAccountId(), e);
            return ResultObject.failed("保存好评配置失败: " + e.getMessage());
        }
    }

    /**
     * 立即评价该账号全部待评价订单
     * 已评价过的订单会被幂等键跳过，重复点击不会重复提交
     */
    @PostMapping("/rate/run")
    public ResultObject<AutoRateService.RateSummary> rateNow(@RequestParam Long xianyuAccountId) {
        try {
            AutoRateService.RateSummary summary = autoRateService.rateNow(xianyuAccountId);
            return ResultObject.success(summary, summary.message());
        } catch (Exception e) {
            log.error("手动好评失败: accountId={}", xianyuAccountId, e);
            return ResultObject.failed("手动好评失败: " + e.getMessage());
        }
    }

    /**
     * 查询好评执行记录
     */
    @GetMapping("/rate/runs")
    public ResultObject<List<XianyuAccountTaskRun>> rateRuns(@RequestParam Long xianyuAccountId,
                                                             @RequestParam(defaultValue = "50") int limit) {
        try {
            int effectiveLimit = Math.min(Math.max(limit, 1), 200);
            return ResultObject.success(
                    runMapper.selectRecent(xianyuAccountId, AutoRateService.TASK_TYPE, effectiveLimit));
        } catch (Exception e) {
            log.error("查询好评记录失败: accountId={}", xianyuAccountId, e);
            return ResultObject.failed("查询好评记录失败: " + e.getMessage());
        }
    }

    /**
     * 校验并规范化 HH:mm 时间；非法返回null
     */
    private static String normalizeTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return "03:00";
        }
        try {
            return LocalTime.parse(raw.trim(), TIME_FORMAT).format(TIME_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
