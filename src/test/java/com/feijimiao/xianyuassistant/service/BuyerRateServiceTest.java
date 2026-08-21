package com.feijimiao.xianyuassistant.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyerRateServiceTest {

    private final BuyerRateService service = new BuyerRateService();

    @Test
    void parsesTradeIdFromNestedStructures() {
        // 平台不同客户端版本把订单号放在不同嵌套层级，解析必须都能命中
        String response = """
                {"ret":["SUCCESS::调用成功"],"data":{"module":{"items":[
                  {"tradeInfo":{"tradeId":"order-1"},"item":{"itemId":"item-1"}},
                  {"orderNo":"order-2","itemId":"item-2"}
                ]}}}""";

        List<BuyerRateService.PendingRateOrder> orders = service.parsePendingOrders(response);

        assertEquals(2, orders.size());
        assertEquals("order-1", orders.get(0).tradeId());
        assertEquals("item-1", orders.get(0).itemId());
        assertEquals("order-2", orders.get(1).tradeId());
        assertEquals("item-2", orders.get(1).itemId());
    }

    @Test
    void skipsItemsWithoutTradeId() {
        String response = """
                {"data":{"module":{"items":[
                  {"someField":"value"},
                  {"tradeId":"order-9"}
                ]}}}""";

        List<BuyerRateService.PendingRateOrder> orders = service.parsePendingOrders(response);

        assertEquals(1, orders.size());
        assertEquals("order-9", orders.get(0).tradeId());
    }

    @Test
    void toleratesMissingItemId() {
        List<BuyerRateService.PendingRateOrder> orders =
                service.parsePendingOrders("{\"data\":{\"module\":{\"items\":[{\"tradeId\":\"order-3\"}]}}}");

        assertEquals(1, orders.size());
        assertEquals("", orders.get(0).itemId());
    }

    @Test
    void returnsEmptyOnMalformedOrEmptyResponse() {
        assertTrue(service.parsePendingOrders(null).isEmpty());
        assertTrue(service.parsePendingOrders("").isEmpty());
        assertTrue(service.parsePendingOrders("not-json").isEmpty());
        // ret 成功但没有 items 数组
        assertTrue(service.parsePendingOrders("{\"ret\":[\"SUCCESS\"],\"data\":{}}").isEmpty());
    }

    @Test
    void ignoresNullLiteralTradeId() {
        // 平台会把空字段序列化成字符串 "null"，不能当成有效订单号
        List<BuyerRateService.PendingRateOrder> orders =
                service.parsePendingOrders("{\"data\":{\"module\":{\"items\":[{\"tradeId\":null,\"orderNo\":\"order-4\"}]}}}");

        assertEquals(1, orders.size());
        assertEquals("order-4", orders.get(0).tradeId());
    }
}
