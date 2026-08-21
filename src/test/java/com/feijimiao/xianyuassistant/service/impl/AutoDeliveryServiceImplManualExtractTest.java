package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.ManualExtractDeliveryRespDTO;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.SentMessageSaveService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryStrategyResolver;
import com.feijimiao.xianyuassistant.service.delivery.OrderDetailFetcher;
import com.feijimiao.xianyuassistant.service.delivery.SkuResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 手动提取发货内容（人工兜底发货）的单元测试
 */
class AutoDeliveryServiceImplManualExtractTest {

    private AutoDeliveryServiceImpl service;
    private XianyuGoodsOrderMapper orderMapper;
    private XianyuGoodsAutoDeliveryConfigMapper configMapper;
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;
    private DeliveryStrategyResolver strategyResolver;
    private OrderDetailFetcher detailFetcher;
    private SkuResolver skuResolver;
    private WebSocketService webSocketService;
    private SentMessageSaveService sentMessageSaveService;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        service = new AutoDeliveryServiceImpl();
        orderMapper = mock(XianyuGoodsOrderMapper.class);
        configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        kamiUsageRecordMapper = mock(XianyuKamiUsageRecordMapper.class);
        strategyResolver = mock(DeliveryStrategyResolver.class);
        detailFetcher = mock(OrderDetailFetcher.class);
        skuResolver = mock(SkuResolver.class);
        webSocketService = mock(WebSocketService.class);
        sentMessageSaveService = mock(SentMessageSaveService.class);
        orderService = mock(OrderService.class);

        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);
        ReflectionTestUtils.setField(service, "kamiUsageRecordMapper", kamiUsageRecordMapper);
        ReflectionTestUtils.setField(service, "deliveryStrategyResolver", strategyResolver);
        ReflectionTestUtils.setField(service, "orderDetailFetcher", detailFetcher);
        ReflectionTestUtils.setField(service, "skuResolver", skuResolver);
        ReflectionTestUtils.setField(service, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(service, "sentMessageSaveService", sentMessageSaveService);
        ReflectionTestUtils.setField(service, "orderService", orderService);
    }

    private XianyuGoodsOrder pendingRecord() {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setId(100L);
        record.setXianyuAccountId(1L);
        record.setXyGoodsId("goods-1");
        record.setOrderId("order-1");
        record.setBuyerUserName("buyer");
        record.setSkuName("新号");
        record.setState(0);
        return record;
    }

    private XianyuGoodsAutoDeliveryConfig kamiConfig(Integer autoConfirmShipment) {
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setDeliveryMode(2);
        config.setKamiConfigIds("7");
        config.setKamiDeliveryTemplate("凭证：{kmKey}");
        config.setAutoConfirmShipment(autoConfirmShipment);
        return config;
    }

    @Test
    void extractsOneKamiPerPurchasedUnitAndMarksRecordDeliveredWithoutSending() {
        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.skuId = "sku-1";
        detail.buyNum = 2;
        detail.buyerUserName = "buyer";

        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(pendingRecord());
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdAndSkuId(1L, "goods-1", "sku-1")).thenReturn(kamiConfig(0));
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(kamiConfig(0));
        when(kamiUsageRecordMapper.selectCount(any())).thenReturn(0L);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(2)))
                .thenReturn(List.of("凭证：CARD-001", "凭证：CARD-002"));

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertEquals(200, result.getCode());
        ManualExtractDeliveryRespDTO data = result.getData();
        assertNotNull(data);
        assertEquals(2, data.getBuyNum());
        assertEquals(2, data.getKamiCount());
        assertEquals("凭证：CARD-001\n凭证：CARD-002", data.getContent());
        assertFalse(data.getConfirmShipmentTriggered());
        assertFalse(data.getReused());
        assertNull(data.getWarning());

        // 订单记录置为已发货并打上人工发货标记
        verify(orderMapper).updateStateContentAndFailReason(100L, 1, "凭证：CARD-001\n凭证：CARD-002", null);
        verify(orderMapper).updateDeliveryWay(100L, 1);
        // 人工提取只取内容，不经系统发送
        verifyNoInteractions(webSocketService);
        verifyNoInteractions(sentMessageSaveService);
        verifyNoInteractions(orderService);
    }

    @Test
    void fallsBackToLocalRecordWhenOrderDetailFetchFails() {
        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(pendingRecord());
        // 风控掉线场景：Cookie失效导致订单详情拿不到，不能中断提取
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(null);
        when(skuResolver.resolveSkuIdByText(1L, "goods-1", "新号")).thenReturn("sku-1");
        when(configMapper.findByAccountIdAndGoodsIdAndSkuId(1L, "goods-1", "sku-1")).thenReturn(kamiConfig(0));
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(kamiConfig(0));
        when(kamiUsageRecordMapper.selectCount(any())).thenReturn(0L);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(1))).thenReturn(List.of("凭证：CARD-001"));

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertEquals(200, result.getCode());
        assertEquals("凭证：CARD-001", result.getData().getContent());
        assertNotNull(result.getData().getWarning());
        // buyNum 本地也没有，降级为1
        assertEquals(1, result.getData().getBuyNum());
        verify(skuResolver).resolveSkuIdByText(1L, "goods-1", "新号");
        verify(orderMapper, never()).updateOrderDetail(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void triggersConfirmShipmentWhenGoodsHasAutoConfirmEnabled() {
        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.buyNum = 1;

        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(pendingRecord());
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(kamiConfig(1));
        when(kamiUsageRecordMapper.selectCount(any())).thenReturn(0L);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(1))).thenReturn(List.of("凭证：CARD-001"));
        when(orderService.confirmShipment(1L, "order-1")).thenReturn("确认成功");

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertEquals(200, result.getCode());
        assertTrue(result.getData().getConfirmShipmentTriggered());
    }

    @Test
    void reportsReuseWhenOrderAlreadyHasKamiUsageRecords() {
        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.buyNum = 1;

        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(pendingRecord());
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(kamiConfig(0));
        // 此前自动发货已扣过卡密（发送失败），本次复用不再扣库存
        when(kamiUsageRecordMapper.selectCount(any())).thenReturn(1L);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(1))).thenReturn(List.of("凭证：CARD-001"));

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertTrue(result.getData().getReused());
    }

    @Test
    void failsWithoutTouchingRecordWhenKamiStockCannotCoverPurchasedUnits() {
        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.buyNum = 3;

        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(pendingRecord());
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(kamiConfig(0));
        when(kamiUsageRecordMapper.selectCount(any())).thenReturn(0L);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(3))).thenReturn(List.of("凭证：CARD-001"));

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertEquals(500, result.getCode());
        assertTrue(result.getMsg().contains("卡密库存不足"));
        verify(orderMapper, never()).updateStateContentAndFailReason(any(), any(), any(), any());
        verify(orderMapper, never()).updateDeliveryWay(any(), any());
    }

    @Test
    void failsWhenGoodsHasNoDeliveryConfig() {
        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(pendingRecord());
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(null);
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(null);

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertEquals(500, result.getCode());
        assertEquals("无匹配的发货配置", result.getMsg());
    }

    @Test
    void failsWhenOrderRecordDoesNotExist() {
        when(orderMapper.selectByAccountIdAndOrderId(1L, "order-1")).thenReturn(null);

        ResultObject<ManualExtractDeliveryRespDTO> result = service.manualExtractDelivery(1L, "order-1");

        assertEquals(500, result.getCode());
        assertEquals("订单记录不存在", result.getMsg());
    }

    @Test
    void rejectsBlankOrderId() {
        assertEquals("订单ID不能为空", service.manualExtractDelivery(1L, "  ").getMsg());
        assertEquals("闲鱼账号ID不能为空", service.manualExtractDelivery(null, "order-1").getMsg());
    }
}
