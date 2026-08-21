package com.feijimiao.xianyuassistant.config.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import reactor.core.publisher.Flux;

/**
 * 动态AI ChatClient管理器
 * 从数据库读取API Key，动态创建/重建ChatClient，无需重启服务
 * 线程安全：使用ReadWriteLock保护ChatClient的读写
 *
 * @author IAMLZY
 * @date 2026/4/23
 */
@Slf4j
@Component
public class DynamicAIChatClientManager {

    private static final String AI_API_KEY_SETTING = "ai_api_key";
    private static final String AI_BASE_URL_SETTING = "ai_base_url";
    private static final String AI_MODEL_SETTING = "ai_model";
    private static final String AI_PROVIDER_SETTING = "ai_provider";

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode";
    private static final String DEFAULT_MODEL = "deepseek-v3";
    private static final String DEFAULT_PROVIDER = "openai-compatible";
    private static final String CLAUDE_DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String CLAUDE_DEFAULT_MODEL = "claude-sonnet-4-20250514";

    @Autowired
    @Lazy
    private SysSettingService sysSettingService;

    @Value("${ai.enabled:false}")
    private boolean aiEnabled;

    /** 当前缓存的API Key，用于判断是否需要重建 */
    private volatile String cachedApiKey;

    /** 当前缓存的Base URL */
    private volatile String cachedBaseUrl;

    /** 当前缓存的Model */
    private volatile String cachedModel;

    /** 当前缓存的供应商 */
    private volatile String cachedProvider;

    /** 当前ChatClient实例 */
    private volatile ChatClient chatClient;

    /** 读写锁，保护ChatClient的线程安全 */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 获取ChatClient实例
     * 如果API Key未配置或为空，返回null
     * 如果API Key发生变化，自动重建ChatClient
     *
     * @return ChatClient实例，未配置API Key时返回null
     */
    public ChatClient getChatClient() {
        if (!aiEnabled) {
            log.debug("[DynamicAI] AI功能未启用(ai.enabled=false)");
            return null;
        }

        // 从数据库读取当前配置
        String currentApiKey = getSettingValue(AI_API_KEY_SETTING);
        String currentBaseUrl = getSettingValue(AI_BASE_URL_SETTING);
        String currentModel = getSettingValue(AI_MODEL_SETTING);
        String currentProvider = normalizeProvider(getSettingValue(AI_PROVIDER_SETTING));

        if (currentApiKey == null || currentApiKey.trim().isEmpty()) {
            log.debug("[DynamicAI] API Key未配置，AI功能不可用");
            return null;
        }

        // 检查配置是否变化，需要重建
        boolean needRebuild = chatClient == null
                || !currentApiKey.equals(cachedApiKey)
                || !safeEquals(currentBaseUrl, cachedBaseUrl)
                || !safeEquals(currentModel, cachedModel)
                || !safeEquals(currentProvider, cachedProvider);

        if (needRebuild) {
            lock.writeLock().lock();
            try {
                // 双重检查，防止并发重建
                boolean stillNeedRebuild = chatClient == null
                        || !currentApiKey.equals(cachedApiKey)
                        || !safeEquals(currentBaseUrl, cachedBaseUrl)
                        || !safeEquals(currentModel, cachedModel)
                        || !safeEquals(currentProvider, cachedProvider);

                if (stillNeedRebuild) {
                    log.info("[DynamicAI] 检测到AI配置变化，重建ChatClient: baseUrl={}, model={}, apiKey={}***{}",
                            currentBaseUrl, currentModel,
                            currentApiKey.substring(0, Math.min(4, currentApiKey.length())),
                            currentApiKey.length() > 8 ? currentApiKey.substring(currentApiKey.length() - 4) : "****");

                    chatClient = buildChatClient(currentApiKey, currentBaseUrl, currentModel, currentProvider);
                    cachedApiKey = currentApiKey;
                    cachedBaseUrl = currentBaseUrl;
                    cachedModel = currentModel;
                    cachedProvider = currentProvider;

                    log.info("[DynamicAI] ChatClient重建完成");
                }
            } catch (Exception e) {
                log.error("[DynamicAI] ChatClient重建失败", e);
                chatClient = null;
                cachedApiKey = null;
            } finally {
                lock.writeLock().unlock();
            }
        }

        lock.readLock().lock();
        try {
            return chatClient;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 检查AI是否可用（API Key已配置且ai.enabled=true）
     */
    public boolean isAvailable() {
        if (!aiEnabled) {
            return false;
        }
        String apiKey = getSettingValue(AI_API_KEY_SETTING);
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * 获取AI状态信息
     */
    public AIStatusInfo getStatusInfo() {
        AIStatusInfo info = new AIStatusInfo();
        info.setEnabled(aiEnabled);

        if (!aiEnabled) {
            info.setAvailable(false);
            info.setMessage("AI功能未启用(ai.enabled=false)");
            return info;
        }

        String apiKey = getSettingValue(AI_API_KEY_SETTING);
        String baseUrl = getSettingValue(AI_BASE_URL_SETTING);
        String model = getSettingValue(AI_MODEL_SETTING);
        String provider = normalizeProvider(getSettingValue(AI_PROVIDER_SETTING));

        info.setProvider(provider);
        info.setBaseUrl(effectiveBaseUrl(provider, baseUrl));
        info.setModel(effectiveModel(provider, model));

        if (apiKey == null || apiKey.trim().isEmpty()) {
            info.setAvailable(false);
            info.setMessage("API Key未配置，请在系统设置中配置AI API Key");
        } else {
            info.setAvailable(true);
            info.setApiKeyConfigured(true);
            info.setMessage("AI服务可用");
        }

        return info;
    }

    /**
     * 强制重建ChatClient（配置变更时调用）
     */
    public void forceRebuild() {
        log.info("[DynamicAI] 收到强制重建信号，清除缓存");
        lock.writeLock().lock();
        try {
            cachedApiKey = null;
            cachedBaseUrl = null;
            cachedModel = null;
            cachedProvider = null;
            chatClient = null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 构建ChatClient实例
     */
    private ChatClient buildChatClient(String apiKey, String baseUrl, String model, String provider) {
        if ("claude".equals(provider)) {
            return buildClaudeChatClient(apiKey, baseUrl, model);
        }

        String effectiveBaseUrl = effectiveBaseUrl(provider, baseUrl);
        String effectiveModel = effectiveModel(provider, model);

        // 创建OpenAiApi实例
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(new SimpleApiKey(apiKey.trim()))
                .baseUrl(effectiveBaseUrl)
                .build();

        // 创建ChatModel
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(effectiveModel)
                .temperature(0.7)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();

        // 创建ChatClient
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个闲鱼智能客服助手")
                .build();
    }

    private ChatClient buildClaudeChatClient(String apiKey, String baseUrl, String model) {
        return ChatClient.builder(new ClaudeChatModel(
                        apiKey.trim(), normalizeClaudeBaseUrl(effectiveBaseUrl("claude", baseUrl)),
                        effectiveModel("claude", model)))
                .defaultSystem("你是一个闲鱼智能客服助手")
                .build();
    }

    private static String normalizeClaudeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim().replaceAll("/$", "");
        return normalized.endsWith("/v1") ? normalized.substring(0, normalized.length() - 3) : normalized;
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.trim().isEmpty()) return DEFAULT_PROVIDER;
        String value = provider.trim().toLowerCase();
        return switch (value) {
            case "claude", "anthropic" -> "claude";
            case "openai", "codex", "openai-compatible", "compatible" ->
                    "openai".equals(value) || "codex".equals(value) ? value : DEFAULT_PROVIDER;
            default -> DEFAULT_PROVIDER;
        };
    }

    private static String effectiveBaseUrl(String provider, String baseUrl) {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) return baseUrl.trim();
        return "claude".equals(provider) ? CLAUDE_DEFAULT_BASE_URL : DEFAULT_BASE_URL;
    }

    private static String effectiveModel(String provider, String model) {
        if (model != null && !model.trim().isEmpty()) return model.trim();
        return "claude".equals(provider) ? CLAUDE_DEFAULT_MODEL : DEFAULT_MODEL;
    }

    private String getSettingValue(String key) {
        try {
            return sysSettingService.getSettingValue(key);
        } catch (Exception e) {
            log.warn("[DynamicAI] 读取配置失败: key={}", key, e);
            return null;
        }
    }

    private static boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * AI状态信息
     */
    public static class AIStatusInfo {
        private boolean enabled;
        private boolean available;
        private boolean apiKeyConfigured;
        private String message;
        private String provider;
        private String baseUrl;
        private String model;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public boolean isApiKeyConfigured() { return apiKeyConfigured; }
        public void setApiKeyConfigured(boolean apiKeyConfigured) { this.apiKeyConfigured = apiKeyConfigured; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
    }

    /** Minimal native Anthropic Messages API adapter, used when the optional Spring AI module is unavailable. */
    private static final class ClaudeChatModel implements org.springframework.ai.chat.model.ChatModel {
        private final String apiKey;
        private final String baseUrl;
        private final String model;
        private final WebClient webClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

        private ClaudeChatModel(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl.replaceAll("/$", "");
            this.model = model;
            this.webClient = WebClient.builder().baseUrl(this.baseUrl).build();
        }

        @Override
        public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
            JsonNode response = webClient.post().uri("/v1/messages")
                    .headers(headers -> {
                        headers.set("x-api-key", apiKey);
                        headers.set("anthropic-version", "2023-06-01");
                    })
                    .bodyValue(requestBody(prompt, false))
                    .retrieve().bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(90));
            return toResponse(response);
        }

        @Override
        public Flux<org.springframework.ai.chat.model.ChatResponse> stream(org.springframework.ai.chat.prompt.Prompt prompt) {
            return webClient.post().uri("/v1/messages")
                    .headers(headers -> {
                        headers.set("x-api-key", apiKey);
                        headers.set("anthropic-version", "2023-06-01");
                    })
                    .bodyValue(requestBody(prompt, true))
                    .retrieve().bodyToFlux(String.class)
                    .flatMapIterable(this::parseSseChunk)
                    .map(text -> new org.springframework.ai.chat.model.ChatResponse(
                            List.of(new org.springframework.ai.chat.model.Generation(
                                    new org.springframework.ai.chat.messages.AssistantMessage(text)))));
        }

        @Override
        public org.springframework.ai.chat.prompt.ChatOptions getDefaultOptions() {
            return org.springframework.ai.chat.prompt.ChatOptions.builder().model(model).temperature(0.7).build();
        }

        private java.util.Map<String, Object> requestBody(org.springframework.ai.chat.prompt.Prompt prompt, boolean stream) {
            List<java.util.Map<String, String>> messages = new ArrayList<>();
            prompt.getInstructions().forEach(message -> {
                if (message.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER) {
                    messages.add(java.util.Map.of("role", "user", "content", message.getText()));
                }
            });
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("model", model);
            body.put("max_tokens", 2048);
            body.put("temperature", 0.7);
            body.put("messages", messages);
            if (prompt.getSystemMessage() != null) body.put("system", prompt.getSystemMessage().getText());
            body.put("stream", stream);
            return body;
        }

        private org.springframework.ai.chat.model.ChatResponse toResponse(JsonNode response) {
            String text = response == null ? "" : response.path("content").path(0).path("text").asText("");
            return new org.springframework.ai.chat.model.ChatResponse(List.of(
                    new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage(text))));
        }

        private List<String> parseSseChunk(String chunk) {
            List<String> result = new ArrayList<>();
            for (String line : chunk.split("\\r?\\n")) {
                if (!line.startsWith("data:")) continue;
                try {
                    JsonNode node = objectMapper.readTree(line.substring(5).trim());
                    String text = node.path("delta").path("text").asText("");
                    if (!text.isEmpty()) result.add(text);
                } catch (Exception ignored) {
                    // Ignore keep-alive and non-JSON SSE frames.
                }
            }
            return result;
        }
    }
}
