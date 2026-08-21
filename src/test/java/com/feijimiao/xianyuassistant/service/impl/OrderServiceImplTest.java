package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryStrategyResolver;
import com.feijimiao.xianyuassistant.service.delivery.OrderDetailFetcher;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    @Test
    void apiDeliveryUsesSkuSpecificCredentialConfig() {
        XianyuGoodsAutoDeliveryConfigMapper configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        DeliveryStrategyResolver strategyResolver = mock(DeliveryStrategyResolver.class);
        OrderDetailFetcher detailFetcher = mock(OrderDetailFetcher.class);
        OrderServiceImpl service = spy(new OrderServiceImpl());
        ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "deliveryStrategyResolver", strategyResolver);
        ReflectionTestUtils.setField(service, "orderDetailFetcher", detailFetcher);

        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.skuId = "sku-1";
        detail.buyerUserName = "buyer";
        XianyuGoodsAutoDeliveryConfig skuConfig = new XianyuGoodsAutoDeliveryConfig();
        skuConfig.setDeliveryMode(1);
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdAndSkuId(1L, "goods-1", "sku-1")).thenReturn(skuConfig);
        when(strategyResolver.resolveBatch(eq(1), any(), eq(1))).thenReturn(List.of("SKU专属凭证"));
        doReturn("虚拟发货成功").when(service)
                .consignDummyDelivery(1L, "order-1", "SKU专属凭证", Collections.emptyList());

        assertEquals("虚拟发货成功", service.consignDummyDeliveryWithConfig(1L, "goods-1", "order-1"));

        verify(configMapper).findByAccountIdAndGoodsIdAndSkuId(1L, "goods-1", "sku-1");
        verify(configMapper, never()).findByAccountIdAndGoodsIdNoSku(1L, "goods-1");
    }

    @Test
    void apiDeliverySendsOneCredentialPerPurchasedUnit() {
        XianyuGoodsAutoDeliveryConfigMapper configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        DeliveryStrategyResolver strategyResolver = mock(DeliveryStrategyResolver.class);
        OrderDetailFetcher detailFetcher = mock(OrderDetailFetcher.class);
        OrderServiceImpl service = spy(new OrderServiceImpl());
        ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "deliveryStrategyResolver", strategyResolver);
        ReflectionTestUtils.setField(service, "orderDetailFetcher", detailFetcher);

        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.buyerUserName = "buyer";
        detail.buyNum = 2;
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setDeliveryMode(2);
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(config);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(2))).thenReturn(List.of("卡密A", "卡密B"));
        doReturn("虚拟发货成功").when(service)
                .consignDummyDelivery(1L, "order-1", "卡密A\n卡密B", Collections.emptyList());

        assertEquals("虚拟发货成功", service.consignDummyDeliveryWithConfig(1L, "goods-1", "order-1"));
    }

    @Test
    void apiDeliveryAbortsWhenCredentialStockCannotCoverPurchasedUnits() {
        XianyuGoodsAutoDeliveryConfigMapper configMapper = mock(XianyuGoodsAutoDeliveryConfigMapper.class);
        XianyuGoodsOrderMapper orderMapper = mock(XianyuGoodsOrderMapper.class);
        DeliveryStrategyResolver strategyResolver = mock(DeliveryStrategyResolver.class);
        OrderDetailFetcher detailFetcher = mock(OrderDetailFetcher.class);
        OrderServiceImpl service = spy(new OrderServiceImpl());
        ReflectionTestUtils.setField(service, "autoDeliveryConfigMapper", configMapper);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "deliveryStrategyResolver", strategyResolver);
        ReflectionTestUtils.setField(service, "orderDetailFetcher", detailFetcher);

        OrderDetailFetcher.OrderDetailInfo detail = new OrderDetailFetcher.OrderDetailInfo();
        detail.buyNum = 3;
        XianyuGoodsAutoDeliveryConfig config = new XianyuGoodsAutoDeliveryConfig();
        config.setDeliveryMode(2);
        when(detailFetcher.fetch(1L, "goods-1", "order-1")).thenReturn(detail);
        when(configMapper.findByAccountIdAndGoodsIdNoSku(1L, "goods-1")).thenReturn(config);
        when(strategyResolver.resolveBatch(eq(2), any(), eq(3))).thenReturn(List.of("卡密A", "卡密B"));

        assertNull(service.consignDummyDeliveryWithConfig(1L, "goods-1", "order-1"));

        verify(service, never()).consignDummyDelivery(any(), any(), any(), any());
    }
}
