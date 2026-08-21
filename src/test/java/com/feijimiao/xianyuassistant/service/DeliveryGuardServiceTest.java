package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.mapper.XianyuDeliveryLeaseMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryGuardServiceTest {

    @Test
    void acquiresAndCompletesOrderLease() {
        XianyuDeliveryLeaseMapper mapper = mock(XianyuDeliveryLeaseMapper.class);
        when(mapper.tryAcquire(1L, "order-1", 100L, 200L)).thenReturn(1);
        DeliveryGuardService service = new DeliveryGuardService(mapper, 100L, () -> 100L);

        assertTrue(service.tryAcquire(1L, "order-1"));
        service.markSuccess(1L, "order-1");

        verify(mapper).tryAcquire(1L, "order-1", 100L, 200L);
        verify(mapper).markSuccess(1L, "order-1");
    }

    @Test
    void rejectsBusyOrderLease() {
        XianyuDeliveryLeaseMapper mapper = mock(XianyuDeliveryLeaseMapper.class);
        when(mapper.tryAcquire(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("order-1"), anyLong(), anyLong())).thenReturn(0);
        DeliveryGuardService service = new DeliveryGuardService(mapper);

        assertFalse(service.tryAcquire(1L, "order-1"));
    }

    @Test
    void missingOrderIdNeedsNoPersistentLease() {
        XianyuDeliveryLeaseMapper mapper = mock(XianyuDeliveryLeaseMapper.class);
        DeliveryGuardService service = new DeliveryGuardService(mapper);

        assertTrue(service.tryAcquire(1L, null));
        service.release(1L, null);

        verify(mapper, never()).release(1L, null);
    }
}
