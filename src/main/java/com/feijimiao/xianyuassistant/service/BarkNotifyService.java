package com.feijimiao.xianyuassistant.service;

/**
 * Bark 推送通知服务。
 */
public interface BarkNotifyService {

    boolean isBarkConfigured();

    void sendNotification(String title, String body);

    String sendTestBark();
}
