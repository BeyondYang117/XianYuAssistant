package com.feijimiao.xianyuassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.utils.XianyuApiCallUtils;
import com.feijimiao.xianyuassistant.utils.XianyuApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评价买家平台接口封装
 *
 * <p>两步流程：先用 mtop.taobao.idle.merchant.rate.list/1.0 拉取待评价订单，
 * 再逐单调用 mtop.taobao.idle.rate.create/4.0 提交好评。</p>
 *
 * <p>两个接口的请求要求不同：待评价列表属于卖家中心接口，
 * 必须用 seller.goofish.com 作为来源且响应格式是 json + valueType=string；
 * 评价提交走买家侧默认来源和 originaljson。</p>
 */
@Slf4j
@Service
public class BuyerRateService {

    private static final String RATE_LIST_API = "mtop.taobao.idle.merchant.rate.list";
    private static final String RATE_LIST_API_VERSION = "1.0";

    private static final String RATE_CREATE_API = "mtop.taobao.idle.rate.create";
    private static final String RATE_CREATE_API_VERSION = "4.0";

    /**
     * 待评价筛选值；平台用 sellerRateStatus=5 表示卖家尚未评价
     */
    private static final String SELLER_RATE_STATUS_PENDING = "5";

    /**
     * 单页拉取条数，与分页终止判断保持一致
     */
    private static final int PAGE_SIZE = 50;

    /**
     * 最多翻页数，防止平台返回异常时无限翻页
     */
    private static final int MAX_PAGES = 20;

    /**
     * 默认好评内容
     */
    public static final String DEFAULT_RATE_CONTENT = "不错的买家，交易愉快";

    /**
     * 订单号在平台响应中可能出现的字段名；不同客户端版本层级不一致，需要递归查找
     */
    private static final String[] TRADE_ID_KEYS = {"tradeId", "trade_id", "orderId", "orderNo", "order_no"};

    private static final String[] ITEM_ID_KEYS = {"itemId", "item_id"};

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private XianyuApiCallUtils apiCallUtils;

    @Autowired
    private AccountService accountService;

    /**
     * 待评价订单
     */
    public record PendingRateOrder(String tradeId, String itemId) {
    }

    /**
     * 评价结果
     */
    public record RateResult(boolean success, String message) {

        static RateResult ok() {
            return new RateResult(true, "评价成功");
        }

        static RateResult fail(String message) {
            return new RateResult(false, message);
        }
    }

    /**
     * 拉取账号全部待评价订单（跨页去重）
     *
     * @param accountId 账号ID
     * @return 待评价订单列表；接口失败时返回空列表并已记录日志
     */
    public List<PendingRateOrder> fetchPendingOrders(Long accountId) {
        List<PendingRateOrder> orders = new ArrayList<>();
        // 同一订单可能因分页边界重复出现，用订单号去重避免重复提交评价
        Set<String> seen = new LinkedHashSet<>();

        for (int page = 1; page <= MAX_PAGES; page++) {
            String cookiesStr = accountService.getCookieByAccountId(accountId);
            if (cookiesStr == null || cookiesStr.isBlank()) {
                log.warn("【账号{}】拉取待评价订单中止：无可用Cookie", accountId);
                break;
            }

            Map<String, Object> rateSearchParam = new HashMap<>();
            rateSearchParam.put("sellerRateStatus", SELLER_RATE_STATUS_PENDING);

            Map<String, Object> data = new HashMap<>();
            data.put("pageNumber", page);
            data.put("rowsPerPage", PAGE_SIZE);
            data.put("queryType", "ORDER");
            data.put("rateSearchParam", rateSearchParam);

            // 卖家中心接口：来源必须是 seller.goofish.com，且响应格式与其他接口不同
            XianyuApiUtils.ApiCallOptions options = XianyuApiUtils.ApiCallOptions.create()
                    .version(RATE_LIST_API_VERSION)
                    .sellerReferer()
                    .responseType("json")
                    .query("valueType", "string");

            XianyuApiCallUtils.ApiCallResult result =
                    apiCallUtils.callApiWithOptions(accountId, RATE_LIST_API, data, cookiesStr, options);

            if (!result.isSuccess()) {
                log.warn("【账号{}】拉取待评价订单失败: page={}, err={}", accountId, page, result.getErrorMessage());
                break;
            }

            List<PendingRateOrder> pageOrders = parsePendingOrders(result.getResponse());
            for (PendingRateOrder order : pageOrders) {
                if (seen.add(order.tradeId())) {
                    orders.add(order);
                }
            }

            // 返回不足一页说明已到末页
            if (pageOrders.size() < PAGE_SIZE) {
                break;
            }
        }

        return orders;
    }

    /**
     * 给买家提交好评
     *
     * @param accountId 账号ID
     * @param tradeId 订单号
     * @param feedback 好评内容，空则用默认文案
     * @return 评价结果
     */
    public RateResult rateBuyer(Long accountId, String tradeId, String feedback) {
        if (tradeId == null || tradeId.isBlank()) {
            return RateResult.fail("缺少订单号");
        }

        String cookiesStr = accountService.getCookieByAccountId(accountId);
        if (cookiesStr == null || cookiesStr.isBlank()) {
            return RateResult.fail("账号无可用Cookie");
        }

        String content = (feedback == null || feedback.isBlank()) ? DEFAULT_RATE_CONTENT : feedback.trim();

        Map<String, Object> data = new HashMap<>();
        data.put("tradeId", tradeId);
        // rate=1 是好评；createOrAppend=0 表示新建评价而非追评
        data.put("rate", 1);
        data.put("feedback", content);
        data.put("createOrAppend", 0);

        XianyuApiUtils.ApiCallOptions options = XianyuApiUtils.ApiCallOptions.create()
                .version(RATE_CREATE_API_VERSION);

        XianyuApiCallUtils.ApiCallResult result =
                apiCallUtils.callApiWithOptions(accountId, RATE_CREATE_API, data, cookiesStr, options);

        if (result.isSuccess()) {
            return RateResult.ok();
        }
        String message = result.getErrorMessage() != null ? result.getErrorMessage() : "未知错误";
        return RateResult.fail(message);
    }

    /**
     * 从待评价列表响应中解析订单。
     * 订单号在不同客户端版本里嵌套层级不一致，因此在每个列表项内递归查找已知字段名。
     */
    List<PendingRateOrder> parsePendingOrders(String response) {
        List<PendingRateOrder> orders = new ArrayList<>();
        if (response == null || response.isBlank()) {
            return orders;
        }

        try {
            JsonNode items = objectMapper.readTree(response).path("data").path("module").path("items");
            if (!items.isArray()) {
                return orders;
            }
            for (JsonNode item : items) {
                String tradeId = findField(item, TRADE_ID_KEYS);
                if (tradeId == null || tradeId.isBlank()) {
                    continue;
                }
                String itemId = findField(item, ITEM_ID_KEYS);
                orders.add(new PendingRateOrder(tradeId, itemId == null ? "" : itemId));
            }
        } catch (Exception e) {
            log.error("解析待评价订单列表失败", e);
        }
        return orders;
    }

    /**
     * 在 JSON 子树中递归查找首个非空的目标字段值。
     * 先在当前层级按字段名匹配，再逐级下探，保证浅层的明确字段优先于深层同名字段。
     */
    private static String findField(JsonNode node, String[] keys) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isObject()) {
            for (String key : keys) {
                JsonNode value = node.get(key);
                if (value != null && value.isValueNode()) {
                    String text = value.asText();
                    if (text != null && !text.isBlank() && !"null".equals(text)) {
                        return text;
                    }
                }
            }
            for (JsonNode child : node) {
                String found = findField(child, keys);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                String found = findField(child, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
