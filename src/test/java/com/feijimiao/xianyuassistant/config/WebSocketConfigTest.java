package com.feijimiao.xianyuassistant.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketConfigTest {

    @Test
    void usesFrequentButJitteredCredentialKeepAliveDefaults() {
        WebSocketConfig config = new WebSocketConfig();

        assertEquals(15, config.getCredentialRefreshMinMinutes());
        assertEquals(20, config.getCredentialRefreshMaxMinutes());
    }
}
