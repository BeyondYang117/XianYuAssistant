package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import com.feijimiao.xianyuassistant.utils.XianyuApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 商品擦亮平台接口封装
 *
 * <p>擦亮走 mtop.taobao.idle.item.polish/2.0，失败后降级到 mtop.idle.item.polish/1.0。
 * 平台在同一商品当天已擦亮时会返回 IDLEITEM_POLISH_AGAIN 一类的业务错误，
 * 这属于预期结果而非故障，按成功处理，否则每日定时任务会持续告警。</p>
 */
@Slf4j
@Service
public class ItemPolishService {

    /**
     * 擦亮主接口
     */
    private static final String POLISH_API = "mtop.taobao.idle.item.polish";
    private static final String POLISH_API_VERSION = "2.0";

    /**
     * 擦亮备用接口，主接口不可用时降级
     */
    private static final String POLISH_BACKUP_API = "mtop.idle.item.polish";
    private static final String POLISH_BACKUP_API_VERSION = "1.0";

    /**
     * 平台表示"今天已经擦亮过"的业务返回特征，命中即视为成功
     */
    private static final String[] DUPLICATE_MARKERS = {
            "IDLEITEM_POLISH_AGAIN", "POLISH_DUPLICATE", "已经擦亮", "一天只能擦亮一次", "已擦亮"
    };

    @Autowired
    private XianyuApiCallUtils apiCallUtils;

    @Autowired
    private AccountService accountService;

    /**
     * 擦亮结果
     */
    public record PolishResult(boolean success, boolean alreadyPolished, String message) {

        static PolishResult ok(String message) {
            return new PolishResult(true, false, message);
        }

        static PolishResult duplicate(String message) {
            return new PolishResult(true, true, message);
        }

        static PolishResult fail(String message) {
            return new PolishResult(false, false, message);
        }
    }

    /**
     * 擦亮单个商品
     *
     * <p>Cookie 每次从库中读取，因为令牌过期重试会把刷新后的 Cookie 写回数据库；
     * 批量擦亮若复用同一份快照，刷新之后的调用会全部带着失效签名。</p>
     *
     * @param accountId 账号ID
     * @param itemId 闲鱼商品ID
     * @return 擦亮结果；当天已擦亮记为成功
     */
    public PolishResult polish(Long accountId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return PolishResult.fail("缺少商品ID");
        }

        String cookiesStr = accountService.getCookieByAccountId(accountId);
        if (cookiesStr == null || cookiesStr.isBlank()) {
            return PolishResult.fail("账号无可用Cookie");
        }

        PolishResult primary = callPolish(accountId, cookiesStr, itemId, POLISH_API, POLISH_API_VERSION);
        if (primary.success()) {
            return primary;
        }

        // 主接口失败：可能是接口下线或版本变更，降级到旧版本再试一次。
        // 令牌过期分支已在 apiCallUtils 内部刷新并把新 Cookie 写回数据库，
        // 这里重新读取一次，避免备用调用带着已失效的签名 token。
        log.warn("【账号{}】擦亮主接口失败，尝试备用接口: itemId={}, err={}", accountId, itemId, primary.message());
        String latestCookies = accountService.getCookieByAccountId(accountId);
        if (latestCookies == null || latestCookies.isBlank()) {
            latestCookies = cookiesStr;
        }
        PolishResult backup = callPolish(accountId, latestCookies, itemId, POLISH_BACKUP_API, POLISH_BACKUP_API_VERSION);
        if (backup.success()) {
            return backup;
        }
        return PolishResult.fail("主接口: " + primary.message() + "；备用接口: " + backup.message());
    }

    private PolishResult callPolish(Long accountId, String cookiesStr, String itemId, String api, String version) {
        Map<String, Object> data = new HashMap<>();
        data.put("itemId", itemId);

        XianyuApiUtils.ApiCallOptions options = XianyuApiUtils.ApiCallOptions.create()
                .version(version)
                .spm("a21ybx.item.0.0", "a21ybx.personal.feeds.1.42f86ac21eZ9zd", "42f86ac21eZ9zd");

        XianyuApiCallUtils.ApiCallResult result =
                apiCallUtils.callApiWithOptions(accountId, api, data, cookiesStr, options);

        if (result.isSuccess()) {
            return PolishResult.ok("擦亮成功");
        }

        String message = describe(result);
        if (isDuplicate(message)) {
            return PolishResult.duplicate("商品今天已经擦亮");
        }
        return PolishResult.fail(message);
    }

    /**
     * 汇总错误描述：优先用 errorMessage，其次原始响应，便于识别当天已擦亮的业务返回
     */
    private String describe(XianyuApiCallUtils.ApiCallResult result) {
        StringBuilder sb = new StringBuilder();
        if (result.getErrorMessage() != null) {
            sb.append(result.getErrorMessage());
        }
        if (result.getResponse() != null) {
            sb.append(' ').append(result.getResponse());
        }
        String message = sb.toString().trim();
        return message.isEmpty() ? "未知错误" : message;
    }

    /**
     * 判断是否为"当天已擦亮"的业务返回
     */
    static boolean isDuplicate(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (String marker : DUPLICATE_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
