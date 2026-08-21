package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
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
}
