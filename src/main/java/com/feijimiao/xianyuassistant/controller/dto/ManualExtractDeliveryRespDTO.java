package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 手动提取发货内容（卡密）响应DTO
 *
 * <p>风控掉线无法自动发货时，由人工点击提取：系统按订单SKU匹配发货配置、
 * 按购买数量提取卡密并套用自动发货同一套文案，把内容回显给卖家复制到闲鱼手工发送。</p>
 */
@Data
public class ManualExtractDeliveryRespDTO {

    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 发货模式：1-文本发货，2-卡密发货
     */
    private Integer deliveryMode;

    /**
     * 订单购买数量
     */
    private Integer buyNum;

    /**
     * 实际提取到的份数
     */
    private Integer kamiCount;

    /**
     * 完整发货内容（多份用换行拼接），与订单记录中保存的内容一致
     */
    private String content;

    /**
     * 逐份发货内容，供前端分条复制
     */
    private List<String> contents;

    /**
     * 配置的发货图片URL，提醒人工一并发送
     */
    private List<String> imageUrls;

    /**
     * 是否已提交确认发货（商品开启了自动确认发货开关时为true）
     */
    private Boolean confirmShipmentTriggered;

    /**
     * 本次是否复用了该订单此前已分配的卡密（重复提取时为true，不会重复扣减库存）
     */
    private Boolean reused;

    /**
     * 提示信息，如订单详情获取失败后走了降级逻辑
     */
    private String warning;
}
