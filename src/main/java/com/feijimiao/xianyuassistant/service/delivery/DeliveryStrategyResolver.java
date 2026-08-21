package com.feijimiao.xianyuassistant.service.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 发货内容策略解析器
 *
 * <p>根据 deliveryMode 从策略列表中找到匹配的策略执行。</p>
 * <p>策略列表由Spring自动注入所有 {@link DeliveryContentStrategy} 实现。</p>
 */
@Slf4j
@Component
public class DeliveryStrategyResolver {

    @Autowired
    private List<DeliveryContentStrategy> strategies;

    /**
     * 根据发货模式解析发货内容
     *
     * @param deliveryMode 发货模式（1=文本，2=卡密）
     * @param context      发货上下文
     * @return 发货内容文本，null表示无法发货
     */
    public String resolve(int deliveryMode, DeliveryContext context) {
        for (DeliveryContentStrategy strategy : strategies) {
            if (strategy.supports(deliveryMode)) {
                return strategy.resolve(context);
            }
        }
        log.warn("【账号{}】未知的发货模式: deliveryMode={}", context.getAccountId(), deliveryMode);
        return null;
    }

    /**
     * 根据发货模式解析 count 份发货内容（订单买多份时一次性取齐）
     *
     * @param deliveryMode 发货模式（1=文本，2=卡密）
     * @param context      发货上下文
     * @param count        需要的份数
     * @return 发货内容列表；空列表表示无法发货，长度小于 count 表示库存不足
     */
    public List<String> resolveBatch(int deliveryMode, DeliveryContext context, int count) {
        for (DeliveryContentStrategy strategy : strategies) {
            if (strategy.supports(deliveryMode)) {
                List<String> contents = strategy.resolveBatch(context, count);
                return contents == null ? List.of() : contents;
            }
        }
        log.warn("【账号{}】未知的发货模式: deliveryMode={}", context.getAccountId(), deliveryMode);
        return List.of();
    }
}
