package com.feijimiao.xianyuassistant.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationLogServiceImplTest {

    private final OperationLogServiceImpl service = new OperationLogServiceImpl();

    @Test
    void rejectsRetentionBelowMinimum() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteOldLogs(0));
    }

    @Test
    void rejectsRetentionAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> service.deleteOldLogs(3651));
    }
}
