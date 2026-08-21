package com.feijimiao.xianyuassistant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskRunMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskSettingMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 每日自动擦亮任务
 *
 * <p>按账号配置的北京时间时刻触发，遍历该账号在售商品逐个擦亮。
 * 幂等分两层：账号维度用 last_polish_date 保证一天只跑一轮，
 * 商品维度用 run_key（含日期）保证同一商品当天不会因进程重启被重复调用。</p>
 */
@Slf4j
@Service
public class AutoPolishService {

    /**
     * 擦亮任务类型标识
     */
    public static final String TASK_TYPE = "auto_polish";

    /**
     * 闲鱼按北京时间划分自然日，擦亮额度也按北京时间重置
     */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final String DEFAULT_POLISH_TIME = "03:00";

    /**
     * 单账号单轮最多擦亮的商品数，避免账号商品过多时长时间连续请求平台
     */
    private static final int MAX_ITEMS_PER_RUN = 200;

    /**
     * 执行记录保留天数
     */
    private static final int RUN_RETENTION_DAYS = 30;

    @Autowired
    private XianyuAccountTaskSettingMapper settingMapper;

    @Autowired
    private XianyuAccountTaskRunMapper runMapper;

    @Autowired
    private XianyuGoodsInfoMapper goodsInfoMapper;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ItemPolishService itemPolishService;

    /**
     * 单账号擦亮汇总
     */
    public record PolishSummary(int found, int success, int failed, int skipped, String message) {
    }

    /**
     * 每分钟检查一次是否有账号到达擦亮时刻。
     * 判断本身很轻（一次索引查询），真正的平台调用只在当天首次到点时发生。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void scheduledPolish() {
        try {
            List<XianyuAccountTaskSetting> settings = settingMapper.selectPolishEnabled();
            if (settings.isEmpty()) {
                return;
            }

            ZonedDateTime now = ZonedDateTime.now(BEIJING);
            for (XianyuAccountTaskSetting setting : settings) {
                if (!isPolishDue(setting, now)) {
                    continue;
                }
                try {
                    PolishSummary summary = runPolish(setting.getXianyuAccountId(), now);
                    log.info("【账号{}】每日擦亮完成: {}", setting.getXianyuAccountId(), summary);
                } catch (Exception e) {
                    log.error("【账号{}】每日擦亮失败", setting.getXianyuAccountId(), e);
                }
            }
        } catch (Exception e) {
            log.error("擦亮任务扫描失败", e);
        }
    }

    /**
     * 每天清理一次过期执行记录
     */
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000L, initialDelay = 10 * 60 * 1000L)
    public void cleanupRuns() {
        try {
            long before = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RUN_RETENTION_DAYS);
            int deleted = runMapper.deleteBefore(before);
            if (deleted > 0) {
                log.info("清理账号任务执行记录: {} 条", deleted);
            }
        } catch (Exception e) {
            log.error("清理账号任务执行记录失败", e);
        }
    }

    /**
     * 手动立即擦亮指定账号的全部在售商品
     *
     * @param accountId 账号ID
     * @return 擦亮汇总
     */
    public PolishSummary polishNow(Long accountId) {
        return runPolish(accountId, ZonedDateTime.now(BEIJING));
    }

    /**
     * 判断账号是否到达当日擦亮时刻。
     * 当天已擦亮直接跳过；未擦亮则要求当前时刻不早于配置时间点。
     */
    static boolean isPolishDue(XianyuAccountTaskSetting setting, ZonedDateTime now) {
        String today = now.format(DATE_FORMAT);
        if (today.equals(setting.getLastPolishDate())) {
            return false;
        }
        LocalTime target = parsePolishTime(setting.getPolishTime());
        return !now.toLocalTime().isBefore(target);
    }

    private static LocalTime parsePolishTime(String raw) {
        try {
            return LocalTime.parse(raw == null || raw.isBlank() ? DEFAULT_POLISH_TIME : raw.trim(), TIME_FORMAT);
        } catch (Exception e) {
            log.warn("擦亮时间格式非法，回落到默认值: raw={}", raw);
            return LocalTime.parse(DEFAULT_POLISH_TIME, TIME_FORMAT);
        }
    }

    private PolishSummary runPolish(Long accountId, ZonedDateTime now) {
        String cookiesStr = accountService.getCookieByAccountId(accountId);
        if (cookiesStr == null || cookiesStr.isBlank()) {
            // 没有凭证时不标记已完成，等账号恢复后本日仍可执行
            return new PolishSummary(0, 0, 0, 0, "账号无可用Cookie");
        }

        List<XianyuGoodsInfo> items = selectOnSaleItems(accountId);
        if (items.isEmpty()) {
            // 没有在售商品也算今天已处理，避免每分钟重复扫描
            markDone(accountId, now);
            return new PolishSummary(0, 0, 0, 0, "没有在售商品");
        }

        String today = now.format(DATE_FORMAT);
        int success = 0;
        int failed = 0;
        int skipped = 0;

        for (XianyuGoodsInfo item : items) {
            String itemId = item.getXyGoodId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            String runKey = TASK_TYPE + ":" + accountId + ":" + itemId + ":" + today;
            if (runMapper.tryClaim(runKey, accountId, TASK_TYPE, itemId, System.currentTimeMillis()) == 0) {
                // 已被本进程或上一轮抢占过，当天不再重复调用平台
                skipped++;
                continue;
            }

            ItemPolishService.PolishResult result;
            try {
                result = itemPolishService.polish(accountId, itemId);
            } catch (Exception e) {
                log.error("【账号{}】擦亮商品异常: itemId={}", accountId, itemId, e);
                finishRun(runKey, "failed", 0, 1, "擦亮异常: " + e.getMessage());
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

            // 逐个商品之间插入拟人化延迟，避免连续高频请求触发风控
            HumanLikeDelayUtils.mediumDelay();
        }

        markDone(accountId, now);
        return new PolishSummary(items.size(), success, failed, skipped,
                String.format("擦亮成功%d个，失败%d个，跳过%d个", success, failed, skipped));
    }

    /**
     * 查询账号在售商品；status=0 表示在售
     */
    private List<XianyuGoodsInfo> selectOnSaleItems(Long accountId) {
        QueryWrapper<XianyuGoodsInfo> query = new QueryWrapper<>();
        query.eq("xianyu_account_id", accountId)
             .eq("status", 0)
             .orderByAsc("id")
             .last("LIMIT " + MAX_ITEMS_PER_RUN);
        return goodsInfoMapper.selectList(query);
    }

    private void markDone(Long accountId, ZonedDateTime now) {
        settingMapper.markPolishDone(accountId, now.format(DATE_FORMAT), System.currentTimeMillis());
    }

    private void finishRun(String runKey, String status, int success, int failed, String message) {
        try {
            runMapper.finish(runKey, status, success, failed, truncate(message), System.currentTimeMillis());
        } catch (Exception e) {
            // 平台动作已经执行完毕，本地状态写入失败只影响记录展示；
            // run_key 仍在表中且为 running，当天不会被重复擦亮，因此这里只告警不重试。
            log.error("保存擦亮执行结果失败: runKey={}", runKey, e);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    /**
     * 当天日期字符串，供上层展示
     */
    public static String today() {
        return LocalDate.now(BEIJING).format(DATE_FORMAT);
    }
}
