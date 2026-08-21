package com.feijimiao.xianyuassistant.controller.dto;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import lombok.Data;

@Data
public class ItemWithConfigDTO {
    
    private XianyuGoodsInfo item;
    
    private Integer xianyuAutoDeliveryOn;
    
    private Integer xianyuAutoReplyOn;
    
    private Integer xianyuAutoReplyContextOn;
    
    private Integer xianyuKeywordReplyOn;

    private Integer humanInterventionOn;

    private Integer humanInterventionMinutes;
    
    private Integer autoDeliveryType;
    
    private String autoDeliveryContent;

    /**
     * 拍下未付款自动改价开关：1-开启，0-关闭
     */
    private Integer autoAdjustPriceOn;

    /**
     * 改价目标总价（元）
     */
    private String adjustTargetPrice;
}
