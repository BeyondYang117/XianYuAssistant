package com.feijimiao.xianyuassistant.event.chatMessageEvent.lister;

import com.feijimiao.xianyuassistant.event.chatMessageEvent.ChatMessageData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageEventAdjustPriceListenerTest {

    private static ChatMessageData message(String content) {
        ChatMessageData data = new ChatMessageData();
        data.setXianyuAccountId(1L);
        data.setMsgContent(content);
        return data;
    }

    @Test
    void recognizesOrderCreatedCards() {
        assertTrue(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message("[我已拍下，待付款]")));
        assertTrue(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message("我已拍下")));
        assertTrue(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message("等待买家付款")));
    }

    @Test
    void ignoresPriceModifiedConfirmationCards() {
        // 改价确认卡片会沿用"等待买家付款"文案，若不排除会导致改价自激循环
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(
                message("我已修改价格，等待买家付款")));
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(
                message("TRADE_MODIFY_FEE 等待买家付款")));
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(
                message("卖家修改了价格")));
    }

    @Test
    void ignoresUnrelatedMessages() {
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message("[已付款，待发货]")));
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message("你好，在吗")));
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message("")));
        assertFalse(ChatMessageEventAdjustPriceListener.isOrderCreatedMessage(message(null)));
    }
}
