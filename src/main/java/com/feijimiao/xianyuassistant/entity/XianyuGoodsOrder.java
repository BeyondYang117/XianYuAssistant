package com.feijimiao.xianyuassistant.entity;

import lombok.Data;

/**
 * 商品订单实体类
 */
@Data
public class XianyuGoodsOrder {
    
    private Long id;
    
    private Long xianyuAccountId;
    
    private Long xianyuGoodsId;
    
    private String xyGoodsId;
    
    private String pnmId;
    
    private String orderId;
    
    private String buyerUserId;
    
    private String buyerUserName;
    
    private String sid;
    
    private String content;
    
    private Integer state;
    
    private String failReason;
    
    private Integer confirmState;
    
    private String createTime;
    
    private String goodsTitle;

    private String skuName;

    private String orderCreateTime;

    private String paySuccessTime;

    private String consignTime;

    private String totalPrice;

    private Integer buyNum;

    /**
     * 发货方式：0-自动发货，1-人工提取卡密后手工发货
     */
    private Integer deliveryWay;
}
