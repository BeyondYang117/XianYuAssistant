package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.service.bo.WebSearchResult;

/** Search abstraction kept independent from the AI provider. */
public interface WebSearchService {
    WebSearchResult searchIfNeeded(String query, boolean hasLocalContext);

    WebSearchStatus getStatus();

    record WebSearchStatus(boolean enabled, String baseUrl, long timeoutMs, int maxResults) {}
}
