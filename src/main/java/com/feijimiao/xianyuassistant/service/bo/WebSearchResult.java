package com.feijimiao.xianyuassistant.service.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A bounded, provider-neutral web search response. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchResult {
    private String query;
    private List<WebSearchItem> items;

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebSearchItem {
        private String title;
        private String content;
        private String url;
        private String publishedDate;
    }
}
