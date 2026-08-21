package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskSettingMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 超时未评价提醒任务
 *
 * <p>扫描已发货但买家迟未评价的订单，按账号配置的延迟与间隔发送提醒话术。
 * 不调用任何新的平台接口，只复用现有 WebSocket 发消息链路。</p>
 *
 * <p>幂等靠订单表自身的 review_request_count 乐观锁，而不是任务记录表：
 * 求评价允许按配置发送多次，次数本身就是状态，放在订单行上最直接。</p>
 */
@Slf4j
@Service
public class ReviewRequestService {

    /**
     * 订单时间字段是平台下发的文本，格式随接口而异，逐个尝试
     */
    private static final DateTimeFormatter[] TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")
    };

    /**
     * 订单时间文本不带时区，按北京时间解释
     */
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    /**
     * 单账号单轮最多发送的提醒数，避免积压时长时间连续发消息
     */
    private static final int MAX_SENDS_PER_RUN = 30;

    /**
     * 每批从库里取的候选订单数，配合 id 游标分页
     */
    private static final int CANDIDATE_PAGE_SIZE = 200;

    public static final String DEFAULT_CONTENT = "亲，如果对宝贝还满意的话，麻烦帮忙点个好评哦，感谢支持～";
    public static final int DEFAULT_DELAY_HOURS = 72;
    public static final int DEFAULT_INTERVAL_HOURS = 24;
    public static final int DEFAULT_MAX_ATTEMPTS = 1;

    @Autowired
    private XianyuAccountTaskSettingMapper settingMapper;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    @Autowired
    private EnhancedMessageSendService messageSendService;

    @Autowired
    private WebSocketService webSocketService;

    /**
     * 求评价发送汇总
     */
    public record ReviewRequestSummary(int candidates, int sent, int failed, int skipped, String message) {
    }

    /**
     * 求评价配置的有效值，缺失字段回落到默认
     */
    record ReviewRequestConfig(String content, int delayHours, int intervalHours, int maxAttempts) {

        static ReviewRequestConfig from(XianyuAccountTaskSetting setting) {
            if (setting == null) {
                return new ReviewRequestConfig(DEFAULT_CONTENT, DEFAULT_DELAY_HOURS,
                        DEFAULT_INTERVAL_HOURS, DEFAULT_MAX_ATTEMPTS);
            }
            return new ReviewRequestConfig(
                    blankToDefault(setting.getReviewRequestContent(), DEFAULT_CONTENT),
                    positiveOrDefault(setting.getReviewRequestDelayHours(), DEFAULT_DELAY_HOURS),
                    positiveOrDefault(setting.getReviewRequestIntervalHours(), DEFAULT_INTERVAL_HOURS),
                    positiveOrDefault(setting.getReviewRequestMaxAttempts(), DEFAULT_MAX_ATTEMPTS));
        }

        private static String blankToDefault(String value, String fallback) {
            return (value == null || value.isBlank()) ? fallback : value.trim();
        }

        private static int positiveOrDefault(Integer value, int fallback) {
            return (value == null || value <= 0) ? fallback : value;
        }
    }

    /**
     * 每 10 分钟扫描一次到期订单。
     * 求评价是小时级的时效，不需要更密的扫描。
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 4 * 60 * 1000L)
    public void scheduledReviewRequest() {
        try {
            List<XianyuAccountTaskSetting> settings = settingMapper.selectReviewRequestEnabled();
            for (XianyuAccountTaskSetting setting : settings) {
                try {
                    ReviewRequestSummary summary = runForAccount(setting.getXianyuAccountId(), setting);
                    if (summary.sent() > 0 || summary.failed() > 0) {
                        log.info("【账号{}】超时求评价完成: {}", setting.getXianyuAccountId(), summary);
                    }
                } catch (Exception e) {
                    log.error("【账号{}】超时求评价失败", setting.getXianyuAccountId(), e);
                }
            }
        } catch (Exception e) {
            log.error("超时求评价扫描失败", e);
        }
    }

    /**
     * 手动立即执行该账号的求评价
     *
     * @param accountId 账号ID
     * @return 发送汇总
     */
    public ReviewRequestSummary runNow(Long accountId) {
        return runForAccount(accountId, settingMapper.selectById(accountId));
    }

    private ReviewRequestSummary runForAccount(Long accountId, XianyuAccountTaskSetting setting) {
        // 发消息依赖实时连接；未就绪就整轮跳过，等下次扫描而不是逐单失败
        if (!webSocketService.isConnected(accountId)) {
            return new ReviewRequestSummary(0, 0, 0, 0, "WebSocket 未连接，等待下次扫描");
        }

        ReviewRequestConfig config = ReviewRequestConfig.from(setting);
        long now = System.currentTimeMillis();

        int candidates = 0;
        int sent = 0;
        int failed = 0;
        int skipped = 0;
        long afterId = 0;

        while (sent + failed < MAX_SENDS_PER_RUN) {
            List<XianyuGoodsOrder> batch = orderMapper.selectReviewRequestCandidates(
                    accountId, config.maxAttempts(), afterId, CANDIDATE_PAGE_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            afterId = batch.get(batch.size() - 1).getId();

            for (XianyuGoodsOrder order : batch) {
                candidates++;
                if (!isReviewRequestDue(order, config, now)) {
                    continue;
                }
                if (sent + failed >= MAX_SENDS_PER_RUN) {
                    // 剩余到期订单留给下一轮
                    skipped++;
                    continue;
                }

                int expectedCount = currentCount(order);
                // 先占位再发送：写成功才代表本进程独占该次提醒，
                // 避免并发扫描或手动触发与定时任务同时给同一订单发两条。
                if (orderMapper.markReviewRequested(order.getId(), expectedCount, now) == 0) {
                    skipped++;
                    continue;
                }

                EnhancedMessageSendService.MessageSendResult result;
                try {
                    result = messageSendService.sendMessageWithRetry(
                            accountId, order.getSid(), order.getBuyerUserId(), config.content());
                } catch (Exception e) {
                    log.error("【账号{}】求评价发送异常: orderId={}", accountId, order.getOrderId(), e);
                    failed++;
                    continue;
                }

                if (result == EnhancedMessageSendService.MessageSendResult.SUCCESS) {
                    sent++;
                    log.info("【账号{}】已发送求评价: orderId={}, 第{}次", accountId, order.getOrderId(), expectedCount + 1);
                } else {
                    // 计数已提前占用，不回退：消息可能已到达买家，回退会导致重复打扰。
                    // 宁可这一次提醒记为已用掉，也不重复发送。
                    failed++;
                    log.warn("【账号{}】求评价发送失败: orderId={}, result={}", accountId, order.getOrderId(), result);
                }
            }

            if (batch.size() < CANDIDATE_PAGE_SIZE) {
                break;
            }
        }

        return new ReviewRequestSummary(candidates, sent, failed, skipped,
                String.format("发送%d条，失败%d条，跳过%d条", sent, failed, skipped));
    }

    /**
     * 判断订单是否到达求评价时刻。
     * 首次以发货时间为基准等待 delayHours；已提醒过则以上次提醒时间为基准等待 intervalHours。
     */
    static boolean isReviewRequestDue(XianyuGoodsOrder order, ReviewRequestConfig config, long now) {
        int count = currentCount(order);
        if (count >= config.maxAttempts()) {
            return false;
        }

        long baseAt;
        long waitHours;
        Long lastRequestAt = order.getLastReviewRequestAt();
        if (count > 0 && lastRequestAt != null && lastRequestAt > 0) {
            baseAt = lastRequestAt;
            waitHours = config.intervalHours();
        } else {
            // 发货时间缺失时回落到订单创建时间；两者都解析不出就不发，避免按错误基准提前打扰买家
            baseAt = parseOrderTime(order.getConsignTime());
            if (baseAt <= 0) {
                baseAt = parseOrderTime(order.getCreateTime());
            }
            if (baseAt <= 0) {
                return false;
            }
            waitHours = config.delayHours();
        }

        return now - baseAt >= TimeUnit.HOURS.toMillis(waitHours);
    }

    private static int currentCount(XianyuGoodsOrder order) {
        Integer count = order.getReviewRequestCount();
        return count == null ? 0 : count;
    }

    /**
     * 解析订单时间文本为毫秒时间戳；无法识别时返回 0。
     * 平台既可能下发 "yyyy-MM-dd HH:mm:ss" 文本，也可能下发毫秒时间戳字符串。
     */
    static long parseOrderTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String text = raw.trim();

        // 纯数字按时间戳处理：13 位是毫秒，10 位是秒
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                long value = Long.parseLong(text);
                if (text.length() >= 13) {
                    return value;
                }
                if (text.length() == 10) {
                    return value * 1000;
                }
                return 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        for (DateTimeFormatter format : TIME_FORMATS) {
            try {
                LocalDateTime parsed = LocalDateTime.parse(text, format);
                return parsed.atZone(BEIJING).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // 尝试下一种格式
            }
        }

        // 兜底尝试 ISO-8601（带 T 的形式）
        try {
            return LocalDateTime.parse(text).atZone(BEIJING).toInstant().toEpochMilli();
        } catch (Exception e) {
            log.debug("无法解析订单时间: raw={}", raw);
            return 0;
        }
    }

    /**
     * 当前时间戳，供测试与展示使用
     */
    static long nowMillis() {
        return Instant.now().toEpochMilli();
    }
}
