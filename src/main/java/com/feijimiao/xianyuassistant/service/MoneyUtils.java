package com.feijimiao.xianyuassistant.service;

/**
 * 金额解析工具
 *
 * <p>改价金额全程使用整数分，不经过 double/float。
 * 浮点表示无法精确保存 0.1、0.07 这类值，累加或取整后会产生分级误差，
 * 而改价直接影响买家实付金额，任何误差都不可接受。</p>
 */
public final class MoneyUtils {

    /**
     * 允许的最大金额（分），对应 100 万元
     */
    private static final long MAX_CENTS = 100_000_000L;

    private MoneyUtils() {
    }

    /**
     * 金额格式非法
     */
    public static class InvalidAmountException extends Exception {
        public InvalidAmountException(String message) {
            super(message);
        }
    }

    /**
     * 把以元为单位的十进制金额文本转换为整数分。
     *
     * <p>只接受纯十进制数字、最多两位小数，不接受科学计数法、正负号、千分位分隔符。
     * 允许范围 0.01 到 1000000 元。</p>
     *
     * @param raw 金额文本，如 "12.34"、"5"、"0.5"
     * @return 整数分
     * @throws InvalidAmountException 格式非法或超出允许范围
     */
    public static long parseYuanToCents(String raw) throws InvalidAmountException {
        if (raw == null || raw.isBlank()) {
            throw new InvalidAmountException("金额不能为空");
        }
        String text = raw.trim();

        String wholeText = text;
        String fracText = "";
        int dot = text.indexOf('.');
        if (dot >= 0) {
            wholeText = text.substring(0, dot);
            fracText = text.substring(dot + 1);
            // 多个小数点属于非法输入
            if (fracText.indexOf('.') >= 0) {
                throw new InvalidAmountException("金额格式非法: " + raw);
            }
        }

        if (wholeText.isEmpty() || fracText.length() > 2) {
            throw new InvalidAmountException("金额格式非法，最多两位小数: " + raw);
        }
        if (!isDigits(wholeText) || (!fracText.isEmpty() && !isDigits(fracText))) {
            throw new InvalidAmountException("金额只能包含数字和小数点: " + raw);
        }
        // 位数过多会在 parseLong 时溢出，提前拒绝
        if (wholeText.length() > 10) {
            throw new InvalidAmountException("金额超出允许范围: " + raw);
        }

        long whole = Long.parseLong(wholeText);
        long frac = 0;
        if (!fracText.isEmpty()) {
            frac = Long.parseLong(fracText);
            // "5" 表示 0.5 元即 50 分，"05" 表示 5 分
            if (fracText.length() == 1) {
                frac *= 10;
            }
        }

        long cents = whole * 100 + frac;
        if (cents <= 0) {
            throw new InvalidAmountException("金额必须大于 0: " + raw);
        }
        if (cents > MAX_CENTS) {
            throw new InvalidAmountException("金额不能超过 1000000 元: " + raw);
        }
        return cents;
    }

    /**
     * 把整数分格式化为元的展示文本，用于日志与界面回显
     */
    public static String centsToYuan(long cents) {
        return (cents / 100) + "." + String.format("%02d", cents % 100);
    }

    private static boolean isDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
