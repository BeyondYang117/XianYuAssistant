package com.feijimiao.xianyuassistant.service.reply;

import com.feijimiao.xianyuassistant.entity.XianyuHumanInterventionRecord;
import com.feijimiao.xianyuassistant.mapper.XianyuHumanInterventionRecordMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HumanTakeoverManagerTest {

    @Test
    void persistsTakeoverBeforeCachingIt() {
        XianyuHumanInterventionRecordMapper mapper = mock(XianyuHumanInterventionRecordMapper.class);
        HumanTakeoverManager manager = new HumanTakeoverManager(mapper);

        manager.takeover(1L, "goods-1", "session-1", 10);

        verify(mapper).insert(any(XianyuHumanInterventionRecord.class));
        verify(mapper).deleteExpiredOrOlder(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("session-1"), any());
        assertTrue(manager.isTakenOver(1L, "session-1"));
    }

    @Test
    void reloadsActiveTakeoverAfterRestart() {
        XianyuHumanInterventionRecordMapper mapper = mock(XianyuHumanInterventionRecordMapper.class);
        XianyuHumanInterventionRecord record = new XianyuHumanInterventionRecord();
        record.setEndTime(java.time.LocalDateTime.now().plusMinutes(5)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        when(mapper.findActive(1L, "session-1")).thenReturn(record);
        HumanTakeoverManager manager = new HumanTakeoverManager(mapper);

        assertTrue(manager.isTakenOver(1L, "session-1"));
        verify(mapper).findActive(1L, "session-1");
    }
}
