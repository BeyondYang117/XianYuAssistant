package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskRunMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskSettingMapper;
import com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自动好评买家任务
 *
 * <p>持续扫描待评价订单并统一提交好评。与擦亮不同，好评没有"每天一次"的额度概念，
 * 幂等只按订单维度：run_key 不含日期，同一订单一旦评价过就永久不再重复提交。</p>
 */
@Slf4j
@Service
public class AutoRateService {

    /**
     * 好评任务类型标识
     */
    public static final String TASK_TYPE = "auto_rate";

    /**
     * 单轮最多评价的订单数，避免积压订单过多时长时间连续请求平台
     */
    private static final int MAX_ORDERS_PER_RUN = 100;

    @Autowired
    private XianyuAccountTaskSettingMapper settingMapper;

    @Autowired
    private XianyuAccountTaskRunMapper runMapper;

    @Autowired
    private BuyerRateService buyerRateService;

    @Autowired
    private AccountService accountService;

    /**
     * 单账号好评汇总
     */
    public record RateSummary(int found, int success, int failed, int skipped, String message) {
    }

    /**
     * 定时扫描待评价订单。
     * 间隔比擦亮长，因为待评价列表接口本身有成本，且好评不像擦亮有时效要求。
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 3 * 60 * 1000L)
    public void scheduledRate() {
        try {
            List<XianyuAccountTaskSetting> settings = settingMapper.selectRateEnabled();
            for (XianyuAccountTaskSetting setting : settings) {
                try {
                    RateSummary summary = runRate(setting.getXianyuAccountId(), setting.getRateContent());
                    if (summary.found() > 0) {
                        log.info("【账号{}】自动好评完成: {}", setting.getXianyuAccountId(), summary);
                    }
                } catch (Exception e) {
                    log.error("【账号{}】自动好评失败", setting.getXianyuAccountId(), e);
                }
            }
        } catch (Exception e) {
            log.error("自动好评扫描失败", e);
        }
    }

    /**
     * 手动立即评价该账号全部待评价订单
     *
     * @param accountId 账号ID
     * @return 好评汇总
     */
    public RateSummary rateNow(Long accountId) {
        XianyuAccountTaskSetting setting = settingMapper.selectById(accountId);
        String content = setting != null ? setting.getRateContent() : null;
        return runRate(accountId, content);
    }

    private RateSummary runRate(Long accountId, String rateContent) {
        String cookiesStr = accountService.getCookieByAccountId(accountId);
        if (cookiesStr == null || cookiesStr.isBlank()) {
            // 与"确实没有待评价订单"区分开，否则界面上无法判断是账号掉线还是真的没单
            return new RateSummary(0, 0, 0, 0, "账号无可用Cookie");
        }

        List<BuyerRateService.PendingRateOrder> orders = buyerRateService.fetchPendingOrders(accountId);
        markScan(accountId);

        if (orders.isEmpty()) {
            return new RateSummary(0, 0, 0, 0, "没有待评价订单");
        }

        int success = 0;
        int failed = 0;
        int skipped = 0;
        int processed = 0;

        for (BuyerRateService.PendingRateOrder order : orders) {
            if (processed >= MAX_ORDERS_PER_RUN) {
                // 剩余订单留给下一轮，避免单次执行持续过久
                skipped += orders.size() - processed;
                break;
            }

            // run_key 不含日期：同一订单只该被评价一次，跨天也不能重复提交
            String runKey = TASK_TYPE + ":" + accountId + ":" + order.tradeId();
            if (runMapper.tryClaim(runKey, accountId, TASK_TYPE, order.tradeId(), System.currentTimeMillis()) == 0) {
                skipped++;
                continue;
            }
            processed++;

            BuyerRateService.RateResult result;
            try {
                result = buyerRateService.rateBuyer(accountId, order.tradeId(), rateContent);
            } catch (Exception e) {
                log.error("【账号{}】评价买家异常: tradeId={}", accountId, order.tradeId(), e);
                finishRun(runKey, "failed", 0, 1, "评价异常: " + e.getMessage());
                failed++;
                continue;
            }

            if (result.success()) {
                success++;
                finishRun(runKey, "success", 1, 0, result.message());
            } else {
                failed++;
                finishRun(runKey, "failed", 0, 1, result.message());
            }

            // 订单之间插入拟人化延迟，避免连续高频请求触发风控
            HumanLikeDelayUtils.mediumDelay();
        }

        return new RateSummary(orders.size(), success, failed, skipped,
                String.format("评价成功%d个，失败%d个，跳过%d个", success, failed, skipped));
    }

    private void markScan(Long accountId) {
        try {
            settingMapper.markRateScan(accountId, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("保存好评扫描时间失败: accountId={}", accountId, e);
        }
    }

    private void finishRun(String runKey, String status, int success, int failed, String message) {
        try {
            runMapper.finish(runKey, status, success, failed, truncate(message), System.currentTimeMillis());
        } catch (Exception e) {
            // 评价已提交到平台，本地状态写入失败只影响记录展示；
            // run_key 仍在表中，该订单不会被重复评价，因此只告警不重试。
            log.error("保存好评执行结果失败: runKey={}", runKey, e);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
