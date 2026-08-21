package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyUtilsTest {

    @Test
    void parsesWholeAndFractionalYuan() throws Exception {
        assertEquals(100, MoneyUtils.parseYuanToCents("1"));
        assertEquals(1234, MoneyUtils.parseYuanToCents("12.34"));
        assertEquals(1, MoneyUtils.parseYuanToCents("0.01"));
        // 单位小数是十分位：0.5 元 = 50 分，不是 5 分
        assertEquals(50, MoneyUtils.parseYuanToCents("0.5"));
        assertEquals(5, MoneyUtils.parseYuanToCents("0.05"));
        assertEquals(1050, MoneyUtils.parseYuanToCents("10.5"));
    }

    @Test
    void trimsSurroundingWhitespace() throws Exception {
        assertEquals(1234, MoneyUtils.parseYuanToCents("  12.34  "));
    }

    @Test
    void acceptsUpperBoundAndRejectsBeyond() throws Exception {
        assertEquals(100_000_000L, MoneyUtils.parseYuanToCents("1000000"));
        assertThrows(MoneyUtils.InvalidAmountException.class,
                () -> MoneyUtils.parseYuanToCents("1000000.01"));
    }

    @Test
    void rejectsZeroAndNegative() {
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("0"));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("0.00"));
        // 负号不是合法字符
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("-5"));
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("1.234"));
    }

    @Test
    void rejectsNonNumericAndMalformed() {
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents(null));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents(""));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("   "));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("abc"));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("12.3.4"));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents(".5"));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("1,234"));
        // 科学计数法会被 Double.parseDouble 接受，这里必须拒绝
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("1e3"));
        assertThrows(MoneyUtils.InvalidAmountException.class, () -> MoneyUtils.parseYuanToCents("+5"));
        // 位数过多必须在 parseLong 溢出前被拒绝
        assertThrows(MoneyUtils.InvalidAmountException.class,
                () -> MoneyUtils.parseYuanToCents("99999999999999999999"));
    }

    @Test
    void formatsCentsBackToYuan() {
        assertEquals("12.34", MoneyUtils.centsToYuan(1234));
        assertEquals("0.05", MoneyUtils.centsToYuan(5));
        assertEquals("0.50", MoneyUtils.centsToYuan(50));
        assertEquals("100.00", MoneyUtils.centsToYuan(10000));
    }

    @Test
    void roundTripPreservesValueForTrickyDecimals() throws Exception {
        // 用 double 表示时会产生误差的典型值，整数分必须无损往返
        for (String amount : new String[]{"0.07", "0.10", "1.01", "8.11", "99.99", "123.45"}) {
            long cents = MoneyUtils.parseYuanToCents(amount);
            assertEquals(amount, MoneyUtils.centsToYuan(cents), "往返失败: " + amount);
        }
    }

    @Test
    void identifiesTransientBusyMessagesOnly() {
        assertTrue(AdjustPriceService.isTransientBusy("FAIL_BIZ_CANNOT_MODIFY_FEE::订单状态同步中"));
        assertTrue(AdjustPriceService.isTransientBusy("系统繁忙，请稍后重试"));
        // 终态拒绝不能重试，否则会在订单已付款后反复打平台接口
        assertFalse(AdjustPriceService.isTransientBusy("FAIL_BIZ_ORDER_PAID::订单已付款"));
        assertFalse(AdjustPriceService.isTransientBusy("FAIL_BIZ_ORDER_CLOSED::订单已关闭"));
        assertFalse(AdjustPriceService.isTransientBusy(null));
        assertFalse(AdjustPriceService.isTransientBusy(""));
    }
}
