package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BarkNotifyServiceImplTest {

    private final Map<String, String> settings = new HashMap<>();
    private BarkNotifyServiceImpl service;

    @BeforeEach
    void setUp() {
        SysSettingService settingService = mock(SysSettingService.class);
        when(settingService.getSettingValue(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> settings.get(invocation.getArgument(0)));
        service = new BarkNotifyServiceImpl();
        ReflectionTestUtils.setField(service, "sysSettingService", settingService);

        settings.put("bark_server_url", "https://bark.example.com/");
        settings.put("bark_device_key", "test-device-key");
        settings.put("bark_enabled", "1");
    }

    @Test
    void buildsPushEndpointAndJsonPayload() throws Exception {
        JsonNode payload = new ObjectMapper().readTree(service.buildPayload("测试标题", "测试内容"));

        assertEquals("https://bark.example.com/push", service.getPushUrl());
        assertEquals("test-device-key", payload.path("device_key").asText());
        assertEquals("测试标题", payload.path("title").asText());
        assertEquals("测试内容", payload.path("body").asText());
        assertEquals("闲鱼助手", payload.path("group").asText());
    }
}
