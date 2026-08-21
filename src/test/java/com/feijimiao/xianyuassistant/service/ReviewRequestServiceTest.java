package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewRequestServiceTest {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    /** 2026-08-21 12:00:00 北京时间 */
    private static final long NOW =
            ZonedDateTime.of(2026, 8, 21, 12, 0, 0, 0, BEIJING).toInstant().toEpochMilli();

    private static ReviewRequestService.ReviewRequestConfig config(int delayHours, int intervalHours, int maxAttempts) {
        return new ReviewRequestService.ReviewRequestConfig("求好评", delayHours, intervalHours, maxAttempts);
    }

    private static XianyuGoodsOrder order(String consignTime, Integer count, Long lastRequestAt) {
        XianyuGoodsOrder order = new XianyuGoodsOrder();
        order.setId(1L);
        order.setOrderId("order-1");
        order.setConsignTime(consignTime);
        order.setReviewRequestCount(count);
        order.setLastReviewRequestAt(lastRequestAt);
        return order;
    }

    private static String hoursAgo(long hours) {
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(NOW - TimeUnit.HOURS.toMillis(hours)), BEIJING)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Test
    void dueAfterConfiguredDelayFromShipment() {
        assertTrue(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(72), 0, 0L), config(72, 24, 1), NOW));
        assertTrue(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(100), 0, 0L), config(72, 24, 1), NOW));
    }

    @Test
    void notDueBeforeConfiguredDelay() {
        assertFalse(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(71), 0, 0L), config(72, 24, 1), NOW));
    }

    @Test
    void notDueWhenMaxAttemptsReached() {
        // 已发过 1 次且上限为 1，无论过了多久都不再发送
        assertFalse(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(500), 1, NOW - TimeUnit.HOURS.toMillis(400)), config(72, 24, 1), NOW));
    }

    @Test
    void repeatUsesIntervalFromLastRequestNotShipment() {
        // 发货已 500 小时，但上次提醒是 10 小时前，间隔 24 小时未到
        assertFalse(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(500), 1, NOW - TimeUnit.HOURS.toMillis(10)), config(72, 24, 3), NOW));
        // 上次提醒 24 小时前，到期
        assertTrue(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(500), 1, NOW - TimeUnit.HOURS.toMillis(24)), config(72, 24, 3), NOW));
    }

    @Test
    void notDueWhenShipmentTimeUnparseable() {
        // 基准时间无法确定时不能发送，否则会按错误基准提前打扰买家
        XianyuGoodsOrder order = order("不是时间", 0, 0L);
        assertFalse(ReviewRequestService.isReviewRequestDue(order, config(72, 24, 1), NOW));

        XianyuGoodsOrder noTime = order(null, 0, 0L);
        assertFalse(ReviewRequestService.isReviewRequestDue(noTime, config(72, 24, 1), NOW));
    }

    @Test
    void fallsBackToCreateTimeWhenShipmentMissing() {
        XianyuGoodsOrder order = order(null, 0, 0L);
        order.setCreateTime(hoursAgo(80));
        assertTrue(ReviewRequestService.isReviewRequestDue(order, config(72, 24, 1), NOW));
    }

    @Test
    void treatsNullCountAsZero() {
        // 老订单迁移后该列为 NULL，必须当成"未发送过"
        assertTrue(ReviewRequestService.isReviewRequestDue(
                order(hoursAgo(80), null, null), config(72, 24, 1), NOW));
    }

    @Test
    void parsesMultipleTimeFormats() {
        assertEquals(NOW, ReviewRequestService.parseOrderTime("2026-08-21 12:00:00"));
        assertEquals(NOW, ReviewRequestService.parseOrderTime("2026/08/21 12:00:00"));
        assertEquals(NOW, ReviewRequestService.parseOrderTime("2026-08-21 12:00"));
        assertEquals(NOW, ReviewRequestService.parseOrderTime("2026-08-21T12:00:00"));
        // 毫秒时间戳字符串
        assertEquals(NOW, ReviewRequestService.parseOrderTime(String.valueOf(NOW)));
        // 秒级时间戳
        assertEquals(NOW / 1000 * 1000, ReviewRequestService.parseOrderTime(String.valueOf(NOW / 1000)));
    }

    @Test
    void returnsZeroForUnparseableTime() {
        assertEquals(0, ReviewRequestService.parseOrderTime(null));
        assertEquals(0, ReviewRequestService.parseOrderTime(""));
        assertEquals(0, ReviewRequestService.parseOrderTime("   "));
        assertEquals(0, ReviewRequestService.parseOrderTime("昨天"));
        // 位数不合法的纯数字不能当成时间戳
        assertEquals(0, ReviewRequestService.parseOrderTime("123"));
    }

    @Test
    void configFallsBackOnMissingOrInvalidValues() {
        XianyuAccountTaskSetting setting = new XianyuAccountTaskSetting();
        setting.setReviewRequestContent("  ");
        setting.setReviewRequestDelayHours(0);
        setting.setReviewRequestIntervalHours(-5);
        setting.setReviewRequestMaxAttempts(null);

        ReviewRequestService.ReviewRequestConfig cfg = ReviewRequestService.ReviewRequestConfig.from(setting);

        assertEquals(ReviewRequestService.DEFAULT_CONTENT, cfg.content());
        assertEquals(ReviewRequestService.DEFAULT_DELAY_HOURS, cfg.delayHours());
        assertEquals(ReviewRequestService.DEFAULT_INTERVAL_HOURS, cfg.intervalHours());
        assertEquals(ReviewRequestService.DEFAULT_MAX_ATTEMPTS, cfg.maxAttempts());
    }

    @Test
    void configFromNullSettingUsesAllDefaults() {
        ReviewRequestService.ReviewRequestConfig cfg = ReviewRequestService.ReviewRequestConfig.from(null);
        assertEquals(ReviewRequestService.DEFAULT_CONTENT, cfg.content());
        assertEquals(ReviewRequestService.DEFAULT_DELAY_HOURS, cfg.delayHours());
    }
}
