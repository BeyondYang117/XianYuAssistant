package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 手动提取发货内容（卡密）请求DTO
 */
@Data
public class ManualExtractDeliveryReqDTO {
    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;

    /**
     * 订单ID
     */
    private String orderId;
}
