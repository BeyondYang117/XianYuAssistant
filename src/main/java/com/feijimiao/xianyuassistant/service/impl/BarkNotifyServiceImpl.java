package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.service.BarkNotifyService;
import com.feijimiao.xianyuassistant.service.SysSettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class BarkNotifyServiceImpl implements BarkNotifyService {

    private static final String KEY_SERVER_URL = "bark_server_url";
    private static final String KEY_DEVICE_KEY = "bark_device_key";
    private static final String KEY_ENABLED = "bark_enabled";
    private static final String DEFAULT_SERVER_URL = "https://api.day.app";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SysSettingService sysSettingService;

    @Override
    public boolean isBarkConfigured() {
        return !getDeviceKey().isEmpty();
    }

    @Override
    public void sendNotification(String title, String body) {
        if (!isBarkEnabled()) {
            log.debug("Bark通知未启用，跳过推送: title={}", title);
            return;
        }
        if (!isBarkConfigured()) {
            log.warn("Bark未配置设备Key，跳过推送: title={}", title);
            return;
        }

        String error = doSend(title, body);
        if (error == null) {
            log.info("Bark通知发送成功: title={}", title);
        } else {
            log.error("Bark通知发送失败: title={}, error={}", title, error);
        }
    }

    @Override
    public String sendTestBark() {
        if (!isBarkConfigured()) {
            return "Bark配置不完整，请先填写设备Key";
        }
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return doSend("闲鱼助手 - Bark配置测试", "Bark通知配置已正常工作。\n发送时间：" + time);
    }

    private String doSend(String title, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getPushUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildPayload(title, body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "HTTP " + response.statusCode() + ": " + response.body();
            }

            if (response.body() != null && !response.body().isBlank()) {
                JsonNode result = objectMapper.readTree(response.body());
                if (result.has("code") && result.path("code").asInt() != 200) {
                    return result.path("message").asText(response.body());
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "请求被中断";
        } catch (Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    private boolean isBarkEnabled() {
        String value = getSettingValue(KEY_ENABLED);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    String buildPayload(String title, String body) throws Exception {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("device_key", getDeviceKey());
        payload.put("title", title);
        payload.put("body", body);
        payload.put("group", "闲鱼助手");
        return objectMapper.writeValueAsString(payload);
    }

    private String getDeviceKey() {
        return getSettingValue(KEY_DEVICE_KEY).trim();
    }

    String getPushUrl() {
        String serverUrl = getSettingValue(KEY_SERVER_URL).trim();
        if (serverUrl.isEmpty()) {
            serverUrl = DEFAULT_SERVER_URL;
        }
        serverUrl = serverUrl.replaceAll("/+$", "");
        return serverUrl.endsWith("/push") ? serverUrl : serverUrl + "/push";
    }

    private String getSettingValue(String key) {
        String value = sysSettingService.getSettingValue(key);
        return value != null ? value : "";
    }
}
