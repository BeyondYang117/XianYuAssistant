package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.mapper.XianyuDeliveryLeaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

@Service
public class DeliveryGuardService {

    private static final long DEFAULT_LEASE_MILLIS = TimeUnit.MINUTES.toMillis(10);

    private final XianyuDeliveryLeaseMapper leaseMapper;
    private final long leaseMillis;
    private final LongSupplier clock;

    @Autowired
    public DeliveryGuardService(XianyuDeliveryLeaseMapper leaseMapper) {
        this(leaseMapper, DEFAULT_LEASE_MILLIS, System::currentTimeMillis);
    }

    DeliveryGuardService(XianyuDeliveryLeaseMapper leaseMapper, long leaseMillis, LongSupplier clock) {
        this.leaseMapper = leaseMapper;
        this.leaseMillis = leaseMillis;
        this.clock = clock;
    }

    public boolean tryAcquire(Long accountId, String orderId) {
        if (accountId == null || orderId == null || orderId.isBlank()) {
            return true;
        }
        long now = clock.getAsLong();
        return leaseMapper.tryAcquire(accountId, orderId, now, now + leaseMillis) > 0;
    }

    public void markSuccess(Long accountId, String orderId) {
        if (accountId != null && orderId != null && !orderId.isBlank()) {
            leaseMapper.markSuccess(accountId, orderId);
        }
    }

    public void release(Long accountId, String orderId) {
        if (accountId != null && orderId != null && !orderId.isBlank()) {
            leaseMapper.release(accountId, orderId);
        }
    }
}
