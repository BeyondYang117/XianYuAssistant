package com.feijimiao.xianyuassistant.service.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本发货策略（deliveryMode=1）
 *
 * <p>直接返回配置的文本内容 {@link com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig#getAutoDeliveryContent()}</p>
 */
@Slf4j
@Component
public class TextDeliveryStrategy implements DeliveryContentStrategy {

    @Override
    public boolean supports(int deliveryMode) {
        return deliveryMode == 1;
    }

    @Override
    public List<String> resolveBatch(DeliveryContext context, int count) {
        String content = context.getDeliveryConfig().getAutoDeliveryContent();
        if (content == null || content.isEmpty()) {
            log.warn("【账号{}】文本发货模式下未配置发货内容: xyGoodsId={}", context.getAccountId(), context.getXyGoodsId());
            return List.of();
        }
        // 文本模式下买多份就是把同一段文案重复发送。
        List<String> contents = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            contents.add(content);
        }
        log.info("【账号{}】文本发货模式: 份数={}", context.getAccountId(), count);
        return contents;
    }
}
