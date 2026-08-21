package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.entity.XianyuKamiUsageRecord;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.feijimiao.xianyuassistant.service.KamiConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KamiDeliveryStrategyTest {

    @Test
    void resolvesKamiWhenWebSocketSessionIdIsMissing() {
        KamiConfigService kamiConfigService = mock(KamiConfigService.class);
        XianyuKamiUsageRecordMapper usageRecordMapper = mock(XianyuKamiUsageRecordMapper.class);
        KamiDeliveryStrategy strategy = new KamiDeliveryStrategy();
        ReflectionTestUtils.setField(strategy, "kamiConfigService", kamiConfigService);
        ReflectionTestUtils.setField(strategy, "kamiUsageRecordMapper", usageRecordMapper);

        XianyuKamiItem item = new XianyuKamiItem();
        item.setId(11L);
        item.setKamiContent("CARD-001");
        when(kamiConfigService.acquireKamiBatch(7L, "order-1", 1)).thenReturn(List.of(item));
        when(usageRecordMapper.insertIgnore(any())).thenReturn(1);

        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setKamiConfigIds("7");
        config.setKamiDeliveryTemplate("凭证：{kmKey}");
        DeliveryContext context = DeliveryContext.builder()
                .accountId(1L)
                .xyGoodsId("goods-1")
                .orderId("order-1")
                .buyerUserName("buyer")
                .deliveryConfig(config)
                .build();

        assertEquals("凭证：CARD-001", strategy.resolve(context));

        ArgumentCaptor<XianyuKamiUsageRecord> captor = ArgumentCaptor.forClass(XianyuKamiUsageRecord.class);
        verify(usageRecordMapper).insertIgnore(captor.capture());
        assertNull(captor.getValue().getBuyerUserId());
        assertEquals("buyer", captor.getValue().getBuyerUserName());
    }

    @Test
    void resolvesDistinctKamiForEachPurchasedUnit() {
        KamiConfigService kamiConfigService = mock(KamiConfigService.class);
        XianyuKamiUsageRecordMapper usageRecordMapper = mock(XianyuKamiUsageRecordMapper.class);
        KamiDeliveryStrategy strategy = new KamiDeliveryStrategy();
        ReflectionTestUtils.setField(strategy, "kamiConfigService", kamiConfigService);
        ReflectionTestUtils.setField(strategy, "kamiUsageRecordMapper", usageRecordMapper);

        when(kamiConfigService.acquireKamiBatch(7L, "order-1", 3))
                .thenReturn(List.of(kamiItem(11L, "CARD-001"), kamiItem(12L, "CARD-002"), kamiItem(13L, "CARD-003")));
        when(usageRecordMapper.insertIgnore(any())).thenReturn(1);

        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setKamiConfigIds("7");
        config.setKamiDeliveryTemplate("凭证：{kmKey}");

        List<String> contents = strategy.resolveBatch(context(config), 3);

        assertEquals(List.of("凭证：CARD-001", "凭证：CARD-002", "凭证：CARD-003"), contents);
        verify(usageRecordMapper, times(3)).insertIgnore(any());
    }

    @Test
    void fallsBackToNextConfigWhenFirstConfigRunsOut() {
        KamiConfigService kamiConfigService = mock(KamiConfigService.class);
        XianyuKamiUsageRecordMapper usageRecordMapper = mock(XianyuKamiUsageRecordMapper.class);
        KamiDeliveryStrategy strategy = new KamiDeliveryStrategy();
        ReflectionTestUtils.setField(strategy, "kamiConfigService", kamiConfigService);
        ReflectionTestUtils.setField(strategy, "kamiUsageRecordMapper", usageRecordMapper);

        when(kamiConfigService.acquireKamiBatch(7L, "order-1", 3)).thenReturn(List.of(kamiItem(11L, "CARD-001")));
        when(kamiConfigService.acquireKamiBatch(8L, "order-1", 2)).thenReturn(List.of(kamiItem(21L, "CARD-021")));
        when(usageRecordMapper.insertIgnore(any())).thenReturn(1);

        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setKamiConfigIds("7,8");

        // 两个配置合计只有2张，少于需要的3张：策略如实返回2张，由调用方判定库存不足。
        assertEquals(List.of("CARD-001", "CARD-021"), strategy.resolveBatch(context(config), 3));
    }

    @Test
    void returnsEmptyListWhenNoKamiAvailable() {
        KamiConfigService kamiConfigService = mock(KamiConfigService.class);
        XianyuKamiUsageRecordMapper usageRecordMapper = mock(XianyuKamiUsageRecordMapper.class);
        KamiDeliveryStrategy strategy = new KamiDeliveryStrategy();
        ReflectionTestUtils.setField(strategy, "kamiConfigService", kamiConfigService);
        ReflectionTestUtils.setField(strategy, "kamiUsageRecordMapper", usageRecordMapper);

        when(kamiConfigService.acquireKamiBatch(7L, "order-1", 2)).thenReturn(List.of());

        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setKamiConfigIds("7");

        assertTrue(strategy.resolveBatch(context(config), 2).isEmpty());
        verify(usageRecordMapper, never()).insertIgnore(any());
    }

    private static XianyuKamiItem kamiItem(Long id, String content) {
        XianyuKamiItem item = new XianyuKamiItem();
        item.setId(id);
        item.setKamiContent(content);
        return item;
    }

    private static DeliveryContext context(XianyuGoodsAutoDeliveryConfig config) {
        return DeliveryContext.builder()
                .accountId(1L)
                .xyGoodsId("goods-1")
                .orderId("order-1")
                .buyerUserName("buyer")
                .deliveryConfig(config)
                .build();
    }
}
