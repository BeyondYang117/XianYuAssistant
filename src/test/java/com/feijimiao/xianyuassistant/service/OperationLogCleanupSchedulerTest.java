package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationLogCleanupSchedulerTest {

    @Test
    void cleanupUsesConfiguredRetentionDays() {
        OperationLogService operationLogService = mock(OperationLogService.class);
        OperationLogCleanupScheduler scheduler = new OperationLogCleanupScheduler(operationLogService);
        ReflectionTestUtils.setField(scheduler, "cleanupEnabled", true);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 45);
        when(operationLogService.deleteOldLogs(45)).thenReturn(3);

        scheduler.cleanupOldLogs();

        verify(operationLogService).deleteOldLogs(45);
    }

    @Test
    void cleanupDoesNothingWhenDisabled() {
        OperationLogService operationLogService = mock(OperationLogService.class);
        OperationLogCleanupScheduler scheduler = new OperationLogCleanupScheduler(operationLogService);
        ReflectionTestUtils.setField(scheduler, "cleanupEnabled", false);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 30);

        scheduler.cleanupOldLogs();

        verify(operationLogService, never()).deleteOldLogs(30);
    }
}
