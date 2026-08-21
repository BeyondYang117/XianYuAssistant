package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import com.feijimiao.xianyuassistant.utils.XianyuApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单改价平台接口封装
 *
 * <p>调用 mtop.taobao.idle.trade.user.adjust.price/1.0 修改待付款订单价格。
 * 平台只接受买家尚未付款的订单。</p>
 *
 * <p>改价不是幂等操作，因此结果分三态而非两态：</p>
 * <ul>
 *   <li>成功 —— 平台明确返回业务成功</li>
 *   <li>失败 —— 平台明确拒绝（订单已付款、已关闭等），可安全放弃</li>
 *   <li>结果未知 —— 请求可能已到达平台但响应缺失，禁止自动重试，必须人工核对</li>
 * </ul>
 */
@Slf4j
@Service
public class AdjustPriceService {

    private static final String ADJUST_PRICE_API = "mtop.taobao.idle.trade.user.adjust.price";
    private static final String ADJUST_PRICE_API_VERSION = "1.0";

    /**
     * 平台明确表示"订单状态尚未同步完成、稍后可重试"的返回特征。
     * 只有这些才允许重试；订单已付款、已关闭等终态拒绝必须立即放弃。
     */
    private static final String[] TRANSIENT_BUSY_MARKERS = {
            "CANNOT_MODIFY_FEE", "稍后重试", "稍后再试", "系统繁忙"
    };

    /**
     * 暂时性繁忙的最大尝试次数
     */
    private static final int TRANSIENT_RETRY_LIMIT = 3;

    /**
     * 重试间隔（毫秒）
     */
    private static final long TRANSIENT_RETRY_GAP = 2000L;

    @Autowired
    private XianyuApiCallUtils apiCallUtils;

    @Autowired
    private AccountService accountService;

    /**
     * 改价结果状态
     */
    public enum AdjustStatus {
        /**
         * 平台确认改价成功
         */
        SUCCESS,
        /**
         * 平台明确拒绝，可安全放弃
         */
        REJECTED,
        /**
         * 结果未知：请求可能已被平台执行，禁止自动重放，需人工核对
         */
        UNKNOWN
    }

    /**
     * 改价结果
     */
    public record AdjustResult(AdjustStatus status, String message) {

        public boolean isSuccess() {
            return status == AdjustStatus.SUCCESS;
        }

        static AdjustResult success(String message) {
            return new AdjustResult(AdjustStatus.SUCCESS, message);
        }

        static AdjustResult rejected(String message) {
            return new AdjustResult(AdjustStatus.REJECTED, message);
        }

        static AdjustResult unknown(String message) {
            return new AdjustResult(AdjustStatus.UNKNOWN, message);
        }
    }

    /**
     * 修改待付款订单的总价，并对平台暂时性繁忙做有限重试。
     *
     * @param accountId 账号ID
     * @param orderId 闲鱼订单ID
     * @param priceCents 改价后的订单总价，单位分
     * @return 改价结果
     */
    public AdjustResult adjustPrice(Long accountId, String orderId, long priceCents) {
        if (orderId == null || orderId.isBlank()) {
            return AdjustResult.rejected("缺少订单ID");
        }
        if (priceCents <= 0) {
            return AdjustResult.rejected("改价金额必须大于 0");
        }

        AdjustResult last = AdjustResult.rejected("未执行");
        for (int attempt = 1; attempt <= TRANSIENT_RETRY_LIMIT; attempt++) {
            last = adjustPriceOnce(accountId, orderId, priceCents);

            if (last.status() == AdjustStatus.SUCCESS) {
                return last;
            }
            // 结果未知时绝不重试：重复提交会让价格状态无法判定
            if (last.status() == AdjustStatus.UNKNOWN) {
                return last;
            }
            if (!isTransientBusy(last.message()) || attempt == TRANSIENT_RETRY_LIMIT) {
                return last;
            }

            log.info("【账号{}】改价暂时不可用，稍后重试: orderId={}, attempt={}/{}",
                    accountId, orderId, attempt, TRANSIENT_RETRY_LIMIT);
            try {
                Thread.sleep(TRANSIENT_RETRY_GAP);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AdjustResult.rejected("改价重试等待被中断");
            }
        }
        return last;
    }

    private AdjustResult adjustPriceOnce(Long accountId, String orderId, long priceCents) {
        String cookiesStr = accountService.getCookieByAccountId(accountId);
        if (cookiesStr == null || cookiesStr.isBlank()) {
            return AdjustResult.rejected("账号无可用Cookie");
        }

        Map<String, Object> data = new HashMap<>();
        // modifyFee 是改价后的订单总价，单位为分，必须是整数
        data.put("modifyFee", priceCents);
        // 当前只支持免运费改价，与闲鱼虚拟商品场景一致
        data.put("newTransportFee", "0");
        data.put("orderId", orderId);

        XianyuApiUtils.ApiCallOptions options = XianyuApiUtils.ApiCallOptions.create()
                .version(ADJUST_PRICE_API_VERSION);

        XianyuApiCallUtils.ApiCallResult result;
        try {
            result = apiCallUtils.callApiWithOptions(accountId, ADJUST_PRICE_API, data, cookiesStr, options);
        } catch (Exception e) {
            // 传输层异常：请求可能已到达平台，不能判定为失败
            log.error("【账号{}】改价请求异常: orderId={}", accountId, orderId, e);
            return AdjustResult.unknown("改价请求异常，结果未知，请人工核对: " + e.getMessage());
        }

        if (result.getResponse() == null) {
            // 响应缺失同样无法判定平台是否已执行
            return AdjustResult.unknown("改价响应缺失，结果未知，请人工核对: "
                    + (result.getErrorMessage() == null ? "无响应" : result.getErrorMessage()));
        }

        // ret 为 SUCCESS 还不够：平台会在 data.success 里给出真正的业务结果
        if (result.isSuccess()) {
            if (isBusinessSuccess(result)) {
                return AdjustResult.success("改价成功");
            }
            return AdjustResult.rejected("平台返回成功但业务未生效: " + describe(result));
        }

        return AdjustResult.rejected(describe(result));
    }

    /**
     * 解析 data.success 业务标志
     */
    private boolean isBusinessSuccess(XianyuApiCallUtils.ApiCallResult result) {
        Map<String, Object> data = result.extractData();
        if (data == null) {
            return false;
        }
        Object success = data.get("success");
        if (success instanceof Boolean b) {
            return b;
        }
        if (success instanceof String s) {
            return "true".equalsIgnoreCase(s);
        }
        return false;
    }

    /**
     * 判断是否为平台暂时性繁忙（可重试）
     */
    static boolean isTransientBusy(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (String marker : TRANSIENT_BUSY_MARKERS) {
            if (message.contains(marker)) {
                return true;
            }
        }
        return false;
    }

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
}
