package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageReceivedEvent;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountTaskRunMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.service.AdjustPriceService;
import com.feijimiao.xianyuassistant.service.MoneyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 拍下未付款自动改价监听器
 *
 * <p>监听买家已拍下但尚未付款的交易卡片，按商品配置把订单总价改成指定金额。</p>
 *
 * <p>幂等：复用 xianyu_account_task_run 的 run_key 唯一索引，
 * key 为 adjust_price:{accountId}:{orderId}，不含日期——同一订单只该改价一次。
 * 改价不幂等，重复提交会让价格状态无法判定，因此宁可漏也不能重。</p>
 */
@Slf4j
@Component
public class ChatMessageEventAdjustPriceListener {

    /**
     * 改价任务类型标识
     */
    public static final String TASK_TYPE = "adjust_price";

    /**
     * 拍下未付款卡片的消息文案特征
     */
    private static final String[] ORDER_CREATED_MARKERS = {
            "[我已拍下，待付款]", "我已拍下", "已拍下，待付款", "等待买家付款"
    };

    /**
     * 卖家改价后的确认卡片会沿用"等待买家付款"文案。
     * 如果不排除，改价成功本身会再次触发改价，形成自激循环。
     */
    private static final String[] PRICE_MODIFIED_MARKERS = {
            "TRADE_MODIFY_FEE", "我已修改价格", "修改了价格"
    };

    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;

    @Autowired
    private XianyuAccountTaskRunMapper runMapper;

    @Autowired
    private AdjustPriceService adjustPriceService;

    @Async
    @EventListener
    public void handleChatMessageReceived(ChatMessageReceivedEvent event) {
        ChatMessageData message = event.getMessageData();
        Long accountId = message.getXianyuAccountId();

        try {
            if (!isOrderCreatedMessage(message)) {
                return;
            }
            if (message.getXyGoodsId() == null || message.getOrderId() == null || message.getOrderId().isBlank()) {
                log.warn("【账号{}】拍下消息缺少商品ID或订单ID，无法改价: pnmId={}", accountId, message.getPnmId());
                return;
            }

            XianyuGoodsConfig config = goodsConfigMapper.selectByAccountAndGoodsId(accountId, message.getXyGoodsId());
            if (config == null || !Integer.valueOf(1).equals(config.getAutoAdjustPriceOn())) {
                return;
            }

            long priceCents;
            try {
                priceCents = MoneyUtils.parseYuanToCents(config.getAdjustTargetPrice());
            } catch (MoneyUtils.InvalidAmountException e) {
                log.error("【账号{}】改价配置金额非法，跳过: xyGoodsId={}, target={}, err={}",
                        accountId, message.getXyGoodsId(), config.getAdjustTargetPrice(), e.getMessage());
                return;
            }

            // 抢占后再调用平台：写入成功才代表本次改价由当前执行路径独占
            String runKey = TASK_TYPE + ":" + accountId + ":" + message.getOrderId();
            if (runMapper.tryClaim(runKey, accountId, TASK_TYPE, message.getOrderId(), System.currentTimeMillis()) == 0) {
                log.info("【账号{}】订单已处理过改价，跳过: orderId={}", accountId, message.getOrderId());
                return;
            }

            log.info("【账号{}】检测到拍下未付款，开始改价: orderId={}, target={}元",
                    accountId, message.getOrderId(), MoneyUtils.centsToYuan(priceCents));

            AdjustPriceService.AdjustResult result =
                    adjustPriceService.adjustPrice(accountId, message.getOrderId(), priceCents);

            switch (result.status()) {
                case SUCCESS -> {
                    finishRun(runKey, "success", 1, 0, "改价成功: " + MoneyUtils.centsToYuan(priceCents) + "元");
                    log.info("【账号{}】✅ 改价成功: orderId={}, price={}元",
                            accountId, message.getOrderId(), MoneyUtils.centsToYuan(priceCents));
                }
                case UNKNOWN -> {
                    // 结果未知：平台可能已执行改价，标记待人工核对且不再自动重放
                    finishRun(runKey, "needs_review", 0, 1, result.message());
                    log.error("【账号{}】⚠️ 改价结果未知，需人工核对: orderId={}, err={}",
                            accountId, message.getOrderId(), result.message());
                }
                default -> {
                    finishRun(runKey, "failed", 0, 1, result.message());
                    log.warn("【账号{}】改价被拒绝: orderId={}, err={}",
                            accountId, message.getOrderId(), result.message());
                }
            }
        } catch (Exception e) {
            log.error("【账号{}】处理自动改价异常: pnmId={}", accountId, message.getPnmId(), e);
        }
    }

    /**
     * 判断是否为买家拍下未付款的交易卡片。
     * 排除卖家改价确认卡片，否则改价成功会再次触发改价。
     */
    static boolean isOrderCreatedMessage(ChatMessageData message) {
        String content = message.getMsgContent();
        if (content == null || content.isBlank()) {
            return false;
        }
        if (containsAny(content, PRICE_MODIFIED_MARKERS)) {
            return false;
        }
        return containsAny(content, ORDER_CREATED_MARKERS);
    }

    private static boolean containsAny(String text, String[] markers) {
        for (String marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private void finishRun(String runKey, String status, int success, int failed, String message) {
        try {
            runMapper.finish(runKey, status, success, failed, truncate(message), System.currentTimeMillis());
        } catch (Exception e) {
            // 平台动作已执行完毕，本地状态写入失败只影响记录展示；
            // run_key 仍在表中，该订单不会被重复改价，因此只告警不重试。
            log.error("保存改价执行结果失败: runKey={}", runKey, e);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
