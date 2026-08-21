package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.controller.dto.ConversationSummaryDTO;
import com.feijimiao.xianyuassistant.mapper.projection.ChatConversationRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageServiceImplTest {

    private final ChatMessageServiceImpl service = new ChatMessageServiceImpl();

    @Test
    void extractsSavedReplyImageUrl() {
        XianyuChatMessage message = new XianyuChatMessage();
        message.setContentType(997);
        message.setMsgContent("[图片]https://example.com/reply.jpg");

        assertEquals(List.of("https://example.com/reply.jpg"), service.extractImageUrls(message));
    }

    @Test
    void extractsIncomingImageUrlFromNestedMessageJson() {
        XianyuChatMessage message = new XianyuChatMessage();
        message.setContentType(2);
        message.setCompleteMsg("{\"1\":{\"6\":{\"3\":{\"5\":\"{\\\"contentType\\\":2,"
                + "\\\"image\\\":{\\\"pics\\\":[{\\\"url\\\":\\\"https://example.com/user.jpg\\\"}]}}\"}}}}");

        assertEquals(List.of("https://example.com/user.jpg"), service.extractImageUrls(message));
    }

    @Test
    void extractsIncomingImageUrlFromAlternateImageField() {
        XianyuChatMessage message = new XianyuChatMessage();
        message.setCompleteMsg("{\"contentType\":2,\"imageUrl\":\"https://example.com/alternate.jpg\"}");

        assertEquals(List.of("https://example.com/alternate.jpg"), service.extractImageUrls(message));
    }

    @Test
    void mapsConversationSummaryForWorkbench() {
        ChatConversationRow row = new ChatConversationRow();
        row.setSId("chat-1@goofish");
        row.setPeerUserId("buyer-1");
        row.setPeerUserName("买家");
        row.setXyGoodsId("goods-1");
        row.setLastMessageId(10L);
        row.setLastContentType(1);
        row.setLastMessage("还在吗");
        row.setLastMessageTime(123L);
        row.setLastSenderUserId("buyer-1");
        row.setMessageCount(4);
        row.setNeedsReply(true);

        ConversationSummaryDTO summary = service.toConversationSummary(row);

        assertEquals("chat-1@goofish", summary.getSId());
        assertEquals("buyer-1", summary.getPeerUserId());
        assertEquals("goods-1", summary.getXyGoodsId());
        assertEquals(4, summary.getMessageCount());
        assertEquals(true, summary.getNeedsReply());
    }
}
