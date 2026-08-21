package com.feijimiao.xianyuassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket配置类
 * 参考Python代码的配置参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "websocket")
public class WebSocketConfig {
    
    /**
     * 心跳间隔（秒）
     * 参考Python: HEARTBEAT_INTERVAL = 15
     */
    private int heartbeatInterval = 15;
    
    /**
     * 心跳超时（秒）
     * 参考Python: HEARTBEAT_TIMEOUT = 5
     */
    private int heartbeatTimeout = 5;

    /** 建立连接的超时时间（秒） */
    private int connectTimeout = 20;

    /** 连接保持稳定后才重置重连计数（秒） */
    private int stableConnectionDuration = 60;

    /** 重连延迟随机抖动上限（秒） */
    private int reconnectJitter = 3;

    /**
     * 异常重连前是否额外校验 Cookie。
     * 正常重连直接复用缓存 Token，避免网络抖动放大为 hasLogin + Token 请求风暴。
     */
    private boolean validateCookieBeforeReconnect = false;

    /**
     * 是否由每条 WebSocket 连接主动定时刷新 Token。
     * 默认关闭，统一交给 TokenRefreshService 按服务端有效期刷新。
     */
    private boolean connectionTokenRefreshEnabled = false;
    
    /**
     * Token刷新间隔（秒）
     * 参考Python: TOKEN_REFRESH_INTERVAL = 3600 (1小时)
     */
    private int tokenRefreshInterval = 3600;
    
    /**
     * Token重试间隔（秒）
     * 参考Python: TOKEN_RETRY_INTERVAL = 300 (5分钟)
     */
    private int tokenRetryInterval = 300;

    /**
     * 凭证刷新最小间隔（分钟）
     * 用于Cookie保活和_m_h5_tk兜底刷新
     */
    private int credentialRefreshMinMinutes = 45;

    /**
     * 凭证刷新最大间隔（分钟）
     * 用于Cookie保活和_m_h5_tk兜底刷新
     */
    private int credentialRefreshMaxMinutes = 60;
    
    /**
     * 消息过期时间（毫秒）
     * 参考Python: MESSAGE_EXPIRE_TIME = 300000 (5分钟)
     */
    private long messageExpireTime = 300000L;
    
    /**
     * 人工接管超时（秒）
     * 参考Python: MANUAL_MODE_TIMEOUT = 3600 (1小时)
     */
    private int manualModeTimeout = 3600;
    
    /**
     * 人工接管切换关键词
     * 参考Python: TOGGLE_KEYWORDS = "。"
     */
    private String toggleKeywords = "。";
    
    /**
     * 模拟人工输入延迟
     * 参考Python: SIMULATE_HUMAN_TYPING = False
     */
    private boolean simulateHumanTyping = false;
    
    /**
     * 连接重连延迟（秒）
     * 参考Python: 5秒
     */
    private int reconnectDelay = 5;
    
    /**
     * 最大重连次数
     */
    private int maxReconnectAttempts = 10;
    
    /**
     * 消息发送重试次数
     */
    private int messageRetryAttempts = 3;
    
    /**
     * 消息发送重试延迟（毫秒）
     */
    private long messageRetryDelay = 1000L;
    
    /**
     * 打印原始消息开关（解密后的消息）
     * 用于调试，打印WebSocket接收到的原始消息内容
     */
    private boolean printRawMessage = false;
}
