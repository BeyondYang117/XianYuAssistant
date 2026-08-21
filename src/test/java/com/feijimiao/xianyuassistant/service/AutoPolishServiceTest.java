package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.entity.XianyuAccountTaskSetting;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoPolishServiceTest {

    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private static XianyuAccountTaskSetting setting(String polishTime, String lastPolishDate) {
        XianyuAccountTaskSetting setting = new XianyuAccountTaskSetting();
        setting.setXianyuAccountId(1L);
        setting.setAutoPolishOn(1);
        setting.setPolishTime(polishTime);
        setting.setLastPolishDate(lastPolishDate);
        return setting;
    }

    private static ZonedDateTime beijing(int hour, int minute) {
        return ZonedDateTime.of(2026, 8, 21, hour, minute, 0, 0, BEIJING);
    }

    @Test
    void dueOnceTargetTimeReached() {
        assertTrue(AutoPolishService.isPolishDue(setting("03:00", ""), beijing(3, 0)));
        assertTrue(AutoPolishService.isPolishDue(setting("03:00", ""), beijing(23, 59)));
    }

    @Test
    void notDueBeforeTargetTime() {
        assertFalse(AutoPolishService.isPolishDue(setting("03:00", ""), beijing(2, 59)));
    }

    @Test
    void notDueWhenAlreadyPolishedToday() {
        assertFalse(AutoPolishService.isPolishDue(setting("03:00", "2026-08-21"), beijing(10, 0)));
    }

    @Test
    void dueWhenLastPolishWasYesterday() {
        assertTrue(AutoPolishService.isPolishDue(setting("03:00", "2026-08-20"), beijing(3, 30)));
    }

    @Test
    void invalidPolishTimeFallsBackToDefault() {
        // 非法配置回落到 03:00：02:59 不触发，03:00 触发
        assertFalse(AutoPolishService.isPolishDue(setting("not-a-time", ""), beijing(2, 59)));
        assertTrue(AutoPolishService.isPolishDue(setting("not-a-time", ""), beijing(3, 0)));
    }

    @Test
    void duplicatePolishMessagesTreatedAsAlreadyPolished() {
        assertTrue(ItemPolishService.isDuplicate("{\"ret\":[\"FAIL_BIZ_IDLEITEM_POLISH_AGAIN::今天已擦亮\"]}"));
        assertTrue(ItemPolishService.isDuplicate("一天只能擦亮一次"));
        assertFalse(ItemPolishService.isDuplicate("FAIL_SYS_TOKEN_EXOIRED::令牌过期"));
        assertFalse(ItemPolishService.isDuplicate(null));
    }
}
