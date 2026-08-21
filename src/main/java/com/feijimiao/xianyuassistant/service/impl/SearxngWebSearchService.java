package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.cache.CacheService;
import com.feijimiao.xianyuassistant.service.WebSearchService;
import com.feijimiao.xianyuassistant.service.bo.WebSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** SearXNG JSON client with bounded results, timeout and local cache. */
@Slf4j
@Service
public class SearxngWebSearchService implements WebSearchService {
    private static final String CACHE_PREFIX = "webSearch:searxng:";
    private static final long CACHE_MINUTES = 1;

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    @Value("${WEB_SEARCH_ENABLED:true}")
    private boolean enabled;

    @Value("${WEB_SEARCH_BASE_URL:http://searxng:8080}")
    private String baseUrl;

    @Value("${WEB_SEARCH_TIMEOUT_MS:4000}")
    private long timeoutMs;

    @Value("${WEB_SEARCH_MAX_RESULTS:5}")
    private int maxResults;

    public SearxngWebSearchService(CacheService cacheService, ObjectMapper objectMapper) {
        this.cacheService = cacheService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public WebSearchResult searchIfNeeded(String query, boolean hasLocalContext) {
        if (!enabled || query == null || query.isBlank()) {
            return empty(query);
        }

        String normalized = query.trim();
        if (!needsWebSearch(normalized, hasLocalContext)) {
            return empty(normalized);
        }

        String cacheKey = CACHE_PREFIX + normalized.toLowerCase(Locale.ROOT);
        WebSearchResult cached = cacheService.get(cacheKey, WebSearchResult.class);
        if (cached != null) return cached;

        try {
            String url = normalizedBaseUrl() + "/search";
            JsonNode response = webClient.get()
                    .uri(UriComponentsBuilder.fromUriString(url)
                            .queryParam("q", normalized)
                            .queryParam("format", "json")
                            .queryParam("language", "zh-CN")
                            .queryParam("safesearch", "1")
                            .build().toUri())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(Math.max(500, timeoutMs)))
                    .block();

            WebSearchResult result = parse(normalized, response);
            if (!result.isEmpty()) cacheService.put(cacheKey, result, CACHE_MINUTES, TimeUnit.MINUTES);
            log.info("[Web Search] SearXNG query={}, results={}", normalized, result.getItems().size());
            return result;
        } catch (Exception e) {
            log.warn("[Web Search] SearXNG unavailable, falling back to local context: {}", e.getMessage());
            return empty(normalized);
        }
    }

    @Override
    public WebSearchStatus getStatus() {
        return new WebSearchStatus(enabled, normalizedBaseUrl(), timeoutMs, Math.max(1, Math.min(maxResults, 10)));
    }

    private boolean needsWebSearch(String query, boolean hasLocalContext) {
        String lower = query.toLowerCase(Locale.ROOT);
        boolean businessQuestion = lower.matches(".*(商品|宝贝|订单|下单|购买|付款|价格|多少钱|优惠|库存|有货|发货|物流|快递|退款|退货|售后|卡密|账号|兑换码|包邮).*" );
        if (businessQuestion) return false;
        boolean realtime = lower.matches(".*(今天|现在|当前|最新|实时|新闻|行情|政策|活动|天气|版本|更新|官网|什么时候|何时).*" );
        return realtime || !hasLocalContext;
    }

    private WebSearchResult parse(String query, JsonNode response) {
        List<WebSearchResult.WebSearchItem> items = new ArrayList<>();
        JsonNode results = response == null ? null : response.path("results");
        if (results != null && results.isArray()) {
            for (JsonNode node : results) {
                String url = safeText(node, "url");
                String title = limit(safeText(node, "title"), 240);
                String content = limit(safeText(node, "content"), 600);
                if (url.isBlank() || title.isBlank() && content.isBlank()) continue;
                items.add(new WebSearchResult.WebSearchItem(title, content, url, safeText(node, "publishedDate")));
                if (items.size() >= Math.max(1, Math.min(maxResults, 10))) break;
            }
        }
        return new WebSearchResult(query, items);
    }

    private String normalizedBaseUrl() {
        String value = baseUrl == null || baseUrl.isBlank() ? "http://searxng:8080" : baseUrl.trim();
        return value.replaceAll("/+$", "");
    }

    private String safeText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("").trim();
    }

    private String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private WebSearchResult empty(String query) {
        return new WebSearchResult(query, List.of());
    }
}
