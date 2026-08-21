package com.feijimiao.xianyuassistant.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期清理数据库中的操作日志，避免日志表无限增长。
 */
@Slf4j
@Component
public class OperationLogCleanupScheduler {

    private final OperationLogService operationLogService;

    @Value("${logging.operation-log.cleanup-enabled:true}")
    private boolean cleanupEnabled;

    @Value("${logging.operation-log.retention-days:30}")
    private int retentionDays;

    public OperationLogCleanupScheduler(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /**
     * 每天凌晨 03:30 清理一次，首次启动不会立即删除数据。
     */
    @Scheduled(cron = "${logging.operation-log.cleanup-cron:0 30 3 * * ?}", zone = "Asia/Shanghai")
    public void cleanupOldLogs() {
        if (!cleanupEnabled) {
            return;
        }

        try {
            int deleted = operationLogService.deleteOldLogs(retentionDays);
            if (deleted > 0) {
                log.info("定时清理操作日志完成: retentionDays={}, deleted={}", retentionDays, deleted);
            }
        } catch (Exception e) {
            log.error("定时清理操作日志失败: retentionDays={}", retentionDays, e);
        }
    }
}
