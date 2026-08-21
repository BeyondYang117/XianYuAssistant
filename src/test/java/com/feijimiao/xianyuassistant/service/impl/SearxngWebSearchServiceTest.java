package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.cache.CacheService;
import com.feijimiao.xianyuassistant.service.bo.WebSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SearxngWebSearchServiceTest {
    private SearxngWebSearchService service;

    @BeforeEach
    void setUp() {
        service = new SearxngWebSearchService(mock(CacheService.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "timeoutMs", 500L);
        ReflectionTestUtils.setField(service, "maxResults", 5);
    }

    @Test
    void doesNotSearchForPrivateCommerceQuestions() {
        WebSearchResult result = service.searchIfNeeded("这个商品多少钱，什么时候发货", false);
        assertTrue(result.isEmpty());
    }

    @Test
    void skipsSearchWhenLocalContextCanAnswerNonRealtimeQuestion() {
        WebSearchResult result = service.searchIfNeeded("这个功能怎么使用", true);
        assertTrue(result.isEmpty());
    }

    @Test
    void disabledSearchAlwaysReturnsEmpty() {
        ReflectionTestUtils.setField(service, "enabled", false);
        WebSearchResult result = service.searchIfNeeded("今天有什么最新新闻", false);
        assertTrue(result.isEmpty());
    }
}
