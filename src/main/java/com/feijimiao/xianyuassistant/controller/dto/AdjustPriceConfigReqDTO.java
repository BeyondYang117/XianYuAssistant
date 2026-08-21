package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 更新商品自动改价配置请求DTO
 */
@Data
public class AdjustPriceConfigReqDTO {

    /**
     * 闲鱼账号ID
     */
    private Long xianyuAccountId;

    /**
     * 闲鱼商品ID
     */
    private String xyGoodsId;

    /**
     * 自动改价开关：1-开启，0-关闭
     */
    private Integer autoAdjustPriceOn;

    /**
     * 改价目标总价（元，十进制字符串，最多两位小数）
     */
    private String adjustTargetPrice;
}
