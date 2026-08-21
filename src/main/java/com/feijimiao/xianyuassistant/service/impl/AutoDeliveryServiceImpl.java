package com.feijimiao.xianyuassistant.service.impl;

import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoDeliveryConfig;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsOrder;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsAutoReplyRecord;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsConfig;
import com.feijimiao.xianyuassistant.entity.XianyuKamiUsageRecord;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoDeliveryConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsConfigMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsAutoReplyRecordMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.feijimiao.xianyuassistant.controller.dto.ManualExtractDeliveryRespDTO;
import com.feijimiao.xianyuassistant.service.AutoDeliveryService;
import com.feijimiao.xianyuassistant.service.EmailNotifyService;
import com.feijimiao.xianyuassistant.service.OrderService;
import com.feijimiao.xianyuassistant.service.WebSocketService;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryContext;
import com.feijimiao.xianyuassistant.service.delivery.DeliveryStrategyResolver;
import com.feijimiao.xianyuassistant.service.delivery.OrderDetailFetcher;
import com.feijimiao.xianyuassistant.service.delivery.SkuResolver;
import com.feijimiao.xianyuassistant.utils.HumanLikeDelayUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自动发货服务实现类（编排层）
 *
 * <p>负责发货流程的编排，具体逻辑委托给 delivery 包下的组件：</p>
 * <ul>
 *   <li>{@link OrderDetailFetcher} - 订单详情获取与解析</li>
 *   <li>{@link DeliveryStrategyResolver} - 发货内容策略解析（文本/卡密/自定义）</li>
 * </ul>
 */
@Slf4j
@Service
public class AutoDeliveryServiceImpl implements AutoDeliveryService {
    
    @Autowired
    private XianyuGoodsConfigMapper goodsConfigMapper;
    
    @Autowired
    private XianyuGoodsAutoDeliveryConfigMapper autoDeliveryConfigMapper;
    
    @Autowired
    private XianyuGoodsOrderMapper orderMapper;
    
    @Autowired
    private XianyuGoodsAutoReplyRecordMapper autoReplyRecordMapper;
    
    @Lazy
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private com.feijimiao.xianyuassistant.service.SentMessageSaveService sentMessageSaveService;

    @Autowired
    private EmailNotifyService emailNotifyService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderDetailFetcher orderDetailFetcher;

    @Autowired
    private DeliveryStrategyResolver deliveryStrategyResolver;

    @Autowired
    private SkuResolver skuResolver;

    @Autowired
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;
    
    @Override
    public XianyuGoodsConfig getGoodsConfig(Long accountId, String xyGoodsId) {
        return goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
    }
    
    @Override
    public XianyuGoodsAutoDeliveryConfig getAutoDeliveryConfig(Long accountId, String xyGoodsId) {
        return autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
    }
    
    @Override
    public void saveOrUpdateGoodsConfig(XianyuGoodsConfig config) {
        XianyuGoodsConfig existing = goodsConfigMapper.selectByAccountAndGoodsId(
                config.getXianyuAccountId(), config.getXyGoodsId());
        
        if (existing == null) {
            goodsConfigMapper.insert(config);
        } else {
            config.setId(existing.getId());
            goodsConfigMapper.update(config);
        }
    }
    
    @Override
    public void saveOrUpdateAutoDeliveryConfig(XianyuGoodsAutoDeliveryConfig config) {
        String skuId = config.getSkuId();
        if (skuId != null && skuId.isEmpty()) {
            skuId = null;
            config.setSkuId(null);
        }
        XianyuGoodsAutoDeliveryConfig existingConfig;
        if (skuId != null) {
            existingConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(
                    config.getXianyuAccountId(), config.getXyGoodsId(), skuId);
        } else {
            existingConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(
                    config.getXianyuAccountId(), config.getXyGoodsId());
        }
        
        if (existingConfig == null) {
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setId(existingConfig.getId());
            autoDeliveryConfigMapper.updateById(config);
        }
    }
    
    @Override
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, String content, Integer state) {
        recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, state, null, null);
    }
    
    public void recordAutoDelivery(Long accountId, String xyGoodsId, String buyerUserId, String buyerUserName, 
                                   String content, Integer state, String pnmId, String orderId) {
        XianyuGoodsOrder record = new XianyuGoodsOrder();
        record.setXianyuAccountId(accountId);
        record.setXyGoodsId(xyGoodsId);
        record.setBuyerUserId(buyerUserId);
        record.setBuyerUserName(buyerUserName);
        record.setContent(content);
        record.setState(state);
        record.setPnmId(pnmId != null ? pnmId : "");
        record.setOrderId(orderId != null ? orderId : "");
        record.setConfirmState(0);
        
        orderMapper.insert(record);
    }
    
    @Override
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName) {
        handleAutoDelivery(accountId, xyGoodsId, sId, buyerUserId, buyerUserName, null);
    }
    
    public void handleAutoDelivery(Long accountId, String xyGoodsId, String sId, String buyerUserId, String buyerUserName, String orderId) {
        try {
            log.info("【账号{}】处理自动发货: xyGoodsId={}, sId={}, buyerUserId={}, buyerUserName={}, orderId={}", 
                    accountId, xyGoodsId, sId, buyerUserId, buyerUserName, orderId);
            
            XianyuGoodsConfig goodsConfig = getGoodsConfig(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return;
            }
            
            XianyuGoodsAutoDeliveryConfig deliveryConfig = getAutoDeliveryConfig(accountId, xyGoodsId);
            if (deliveryConfig == null || deliveryConfig.getAutoDeliveryContent() == null || 
                    deliveryConfig.getAutoDeliveryContent().isEmpty()) {
                log.warn("【账号{}】商品未配置自动发货内容: xyGoodsId={}", accountId, xyGoodsId);
                recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
                return;
            }
            
            String content = deliveryConfig.getAutoDeliveryContent();
            log.info("【账号{}】准备发送自动发货消息: content={}", accountId, content);

            HumanLikeDelayUtils.mediumDelay();
            HumanLikeDelayUtils.thinkingDelay();
            HumanLikeDelayUtils.typingDelay(content.length());
            
            String cid = sId.replace("@goofish", "");
            String toId = cid;
            
            boolean success = webSocketService.sendMessage(accountId, cid, toId, content);
            
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, content, success ? 1 : 0, null, orderId);
            
            if (success) {
                log.info("【账号{}】自动发货成功: xyGoodsId={}, buyerUserName={}, content={}", 
                        accountId, xyGoodsId, buyerUserName, content);
                sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
            } else {
                log.error("【账号{}】自动发货失败: xyGoodsId={}", accountId, xyGoodsId);
            }
            
        } catch (Exception e) {
            log.error("【账号{}】自动发货异常: xyGoodsId={}", accountId, xyGoodsId, e);
            recordAutoDelivery(accountId, xyGoodsId, buyerUserId, buyerUserName, null, 0, null, orderId);
        }
    }
    
    @Override
    public void handleAutoReply(Long accountId, String xyGoodsId, String sId, String buyerMessage) {
        log.info("【账号{}】自动回复功能已移除: xyGoodsId={}", accountId, xyGoodsId);
    }
    
    private void recordAutoReply(Long accountId, String xyGoodsId, String buyerMessage, 
                                  String replyContent, String matchedKeyword, Integer state) {
        try {
            XianyuGoodsAutoReplyRecord record = new XianyuGoodsAutoReplyRecord();
            record.setXianyuAccountId(accountId);
            record.setXyGoodsId(xyGoodsId);
            record.setBuyerMessage(buyerMessage);
            record.setReplyContent(replyContent);
            record.setMatchedKeyword(matchedKeyword);
            record.setState(state);
            
            autoReplyRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("【账号{}】记录自动回复失败", accountId, e);
        }
    }
    
    @Override
    public com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO getAutoDeliveryRecords(
            com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordReqDTO reqDTO) {
        
        Long accountId = reqDTO.getXianyuAccountId();
        String xyGoodsId = reqDTO.getXyGoodsId();
        String keyword = reqDTO.getKeyword();
        int pageNum = reqDTO.getPageNum() != null ? reqDTO.getPageNum() : 1;
        int pageSize = reqDTO.getPageSize() != null ? reqDTO.getPageSize() : 20;
        
        int offset = (pageNum - 1) * pageSize;
        
        List<XianyuGoodsOrder> records = orderMapper.selectByAccountIdWithPage(
                accountId, xyGoodsId, keyword, pageSize, offset);
        
        long total = orderMapper.countByAccountId(accountId, xyGoodsId, keyword);
        
        List<com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO> recordDTOs = new ArrayList<>();
        for (XianyuGoodsOrder record : records) {
            com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO dto = 
                    new com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordDTO();
            dto.setId(record.getId());
            dto.setXianyuAccountId(record.getXianyuAccountId());
            dto.setXyGoodsId(record.getXyGoodsId());
            dto.setGoodsTitle(record.getGoodsTitle());
            dto.setBuyerUserName(record.getBuyerUserName());
            dto.setContent(record.getContent());
            dto.setState(record.getState());
            dto.setConfirmState(record.getConfirmState());
            dto.setOrderId(record.getOrderId());
            dto.setSkuName(record.getSkuName());
            dto.setOrderCreateTime(record.getOrderCreateTime());
            dto.setPaySuccessTime(record.getPaySuccessTime());
            dto.setConsignTime(record.getConsignTime());
            dto.setTotalPrice(record.getTotalPrice());
            dto.setBuyNum(record.getBuyNum());
            dto.setDeliveryWay(record.getDeliveryWay());
            dto.setCreateTime(record.getCreateTime());
            recordDTOs.add(dto);
        }
        
        com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO respDTO = 
                new com.feijimiao.xianyuassistant.controller.dto.AutoDeliveryRecordRespDTO();
        respDTO.setRecords(recordDTOs);
        respDTO.setTotal(total);
        respDTO.setPageNum(pageNum);
        respDTO.setPageSize(pageSize);
        
        return respDTO;
    }

    @Override
    public com.feijimiao.xianyuassistant.common.ResultObject<String> triggerAutoDelivery(
            com.feijimiao.xianyuassistant.controller.dto.TriggerAutoDeliveryReqDTO reqDTO) {
        try {
            Long accountId = reqDTO.getXianyuAccountId();
            String xyGoodsId = reqDTO.getXyGoodsId();
            String orderId = reqDTO.getOrderId();
            Boolean needHumanLikeDelay = reqDTO.getNeedHumanLikeDelay() != null ? reqDTO.getNeedHumanLikeDelay() : false;

            log.info("【账号{}】触发自动发货: xyGoodsId={}, orderId={}, needHumanLikeDelay={}", 
                    accountId, xyGoodsId, orderId, needHumanLikeDelay);

            XianyuGoodsOrder record = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (record == null) {
                log.warn("【账号{}】发货记录不存在: orderId={}", accountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货记录不存在");
            }

            String pnmId = record.getPnmId();
            if (pnmId == null || pnmId.isEmpty()) {
                log.warn("【账号{}】发货记录没有pnmId: orderId={}", accountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货记录没有pnmId");
            }

            Long recordId = record.getId();
            String sId = record.getSid() != null ? record.getSid() : record.getBuyerUserId() + "@goofish";
            String buyerUserName = record.getBuyerUserName();

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.info("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("商品未开启自动发货");
            }

            executeDelivery(recordId, accountId, xyGoodsId, sId, orderId, buyerUserName, needHumanLikeDelay);

            XianyuGoodsOrder updatedRecord = orderMapper.selectByOrderId(accountId, xyGoodsId, orderId);
            if (updatedRecord != null && updatedRecord.getState() == 1) {
                return com.feijimiao.xianyuassistant.common.ResultObject.success("触发自动发货成功");
            } else {
                String failReason = updatedRecord != null ? updatedRecord.getFailReason() : "未知错误";
                return com.feijimiao.xianyuassistant.common.ResultObject.failed(failReason != null ? failReason : "发货失败");
            }

        } catch (Exception e) {
            log.error("【账号{}】触发自动发货失败: xyGoodsId={}, orderId={}", 
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getOrderId(), e);
            return com.feijimiao.xianyuassistant.common.ResultObject.failed("触发自动发货失败: " + e.getMessage());
        }
    }

    @Override
    public void executeDelivery(Long recordId, Long accountId, String xyGoodsId, String sId, String orderId, String buyerUserName, boolean needHumanLikeDelay) {
        try {
            log.info("【账号{}】开始执行自动发货: recordId={}, xyGoodsId={}, orderId={}", accountId, recordId, xyGoodsId, orderId);

            XianyuGoodsConfig goodsConfig = goodsConfigMapper.selectByAccountAndGoodsId(accountId, xyGoodsId);
            if (goodsConfig == null || goodsConfig.getXianyuAutoDeliveryOn() == null || goodsConfig.getXianyuAutoDeliveryOn() != 1) {
                log.warn("【账号{}】商品未开启自动发货: xyGoodsId={}", accountId, xyGoodsId);
                updateRecordState(recordId, -1, null, "商品未开启自动发货");
                return;
            }

            OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(accountId, xyGoodsId, orderId);
            if (orderDetail == null && orderId != null && !orderId.isEmpty()) {
                log.warn("【账号{}】获取订单详情失败(可能Cookie过期或API异常)，中断发货: orderId={}", accountId, orderId);
                String failReason = "获取订单详情失败(可能Cookie过期)，请检查Cookie状态";
                updateRecordState(recordId, -1, null, failReason);
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
                return;
            }
            String orderSkuId = orderDetail != null ? orderDetail.skuId : null;
            int buyNum = (orderDetail != null && orderDetail.buyNum != null && orderDetail.buyNum > 0) ? orderDetail.buyNum : 1;
            log.info("【账号{}】订单SKU: orderId={}, skuId={}, buyNum={}", accountId, orderId, orderSkuId, buyNum);

            if (orderDetail != null) {
                orderMapper.updateOrderDetail(recordId, orderDetail.buyerUserName, orderDetail.orderCreateTime, orderDetail.paySuccessTime, orderDetail.consignTime, orderDetail.skuName, orderDetail.goodsTitle, orderDetail.totalPrice, orderDetail.buyNum);
            }

            XianyuGoodsAutoDeliveryConfig deliveryConfig = null;
            if (orderSkuId != null && !orderSkuId.isEmpty()) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(accountId, xyGoodsId, orderSkuId);
            }
            if (deliveryConfig == null) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
            }

            if (deliveryConfig == null) {
                log.warn("【账号{}】商品无匹配的发货配置: xyGoodsId={}, skuId={}", accountId, xyGoodsId, orderSkuId);
                updateRecordState(recordId, -1, null, "无匹配的发货配置");
                return;
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            String cid = sId.replace("@goofish", "");
            String toId = cid;
            boolean wsConnected = webSocketService.isConnected(accountId);
            boolean anySuccess = false;
            StringBuilder allContent = new StringBuilder();

            DeliveryContext ctx = DeliveryContext.builder()
                    .recordId(recordId)
                    .accountId(accountId)
                    .xyGoodsId(xyGoodsId)
                    .sId(sId)
                    .orderId(orderId)
                    .buyerUserName(buyerUserName)
                    .deliveryConfig(deliveryConfig)
                    .build();

            // 买多份的订单一次性把内容取齐：卡密模式下逐次调用会命中订单幂等复用，导致重复发同一张卡密。
            List<String> contents = deliveryStrategyResolver.resolveBatch(deliveryMode, ctx, buyNum);
            if (contents.size() < buyNum) {
                String failMsg = deliveryContentFailMessage(deliveryMode, buyNum, contents.size());
                log.warn("【账号{}】发货内容解析失败: {}", accountId, failMsg);
                updateRecordState(recordId, -1, null, failMsg);
                emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failMsg);
                return;
            }

            if (!wsConnected) {
                log.info("【账号{}】WebSocket未连接，使用虚拟发货API: orderId={}", accountId, orderId);
                String content = String.join("\n", contents);

                List<String> imageUrls = parseImageUrls(deliveryConfig.getAutoDeliveryImageUrl());

                String deliveryResult = orderService.consignDummyDelivery(accountId, orderId, content, imageUrls);
                if (deliveryResult != null) {
                    anySuccess = true;
                    allContent.append(content);
                    log.info("【账号{}】✅ 虚拟发货API成功: recordId={}, result={}", accountId, recordId, deliveryResult);
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
                } else {
                    log.error("【账号{}】❌ 虚拟发货API失败: recordId={}", accountId, recordId);
                    updateRecordState(recordId, -1, content, "虚拟发货API失败");
                    emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "虚拟发货API失败");
                    return;
                }
            } else {

            for (int i = 0; i < buyNum; i++) {
                log.info("【账号{}】发货第{}/{}次: orderId={}", accountId, i + 1, buyNum, orderId);

                String content = contents.get(i);

                if (needHumanLikeDelay) {
                    if (i > 0) {
                        HumanLikeDelayUtils.thinkingDelay();
                    }
                    HumanLikeDelayUtils.mediumDelay();
                    HumanLikeDelayUtils.thinkingDelay();
                    HumanLikeDelayUtils.typingDelay(content.length());
                }

                log.info("【账号{}】准备发送发货文本[{}/{}]: content长度={}, deliveryMode={}", accountId, i + 1, buyNum, content.length(), deliveryMode);
                boolean success = webSocketService.sendMessage(accountId, cid, toId, content);

                if (success && needHumanLikeDelay) {
                    HumanLikeDelayUtils.thinkingDelay();
                }

                sendDeliveryImages(accountId, xyGoodsId, cid, toId, deliveryConfig, needHumanLikeDelay);

                if (success) {
                    anySuccess = true;
                    if (allContent.length() > 0) allContent.append("\n");
                    allContent.append(content);
                    log.info("【账号{}】✅ 发货成功[{}/{}]: recordId={}, deliveryMode={}", accountId, i + 1, buyNum, recordId, deliveryMode);
                    sentMessageSaveService.saveAiAssistantReply(accountId, cid, toId, content, xyGoodsId);
                } else {
                    log.error("【账号{}】❌ 发货失败[{}/{}]: recordId={}", accountId, i + 1, buyNum, recordId);
                    if (i == 0) {
                        updateRecordState(recordId, -1, content, "消息发送失败");
                        emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, "消息发送失败");
                        return;
                    }
                    break;
                }
            }

            } // end else (wsConnected)

            if (anySuccess) {
                updateRecordState(recordId, 1, allContent.toString(), null);
                XianyuGoodsAutoDeliveryConfig baseConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
                boolean autoConfirm = (baseConfig != null && baseConfig.getAutoConfirmShipment() != null && baseConfig.getAutoConfirmShipment() == 1);
                if (autoConfirm) {
                    log.info("【账号{}】检测到自动确认发货开关已开启，准备自动确认发货: orderId={}", accountId, orderId);
                    executeAutoConfirmShipment(accountId, orderId);
                }
            }

        } catch (Exception e) {
            log.error("【账号{}】执行自动发货异常: recordId={}, xyGoodsId={}", accountId, recordId, xyGoodsId, e);
            String failReason = "发货异常: " + e.getMessage();
            updateRecordState(recordId, -1, null, failReason);
            emailNotifyService.sendAutoDeliveryFailEmail(null, xyGoodsId, orderId, failReason);
        }
    }

    private void sendDeliveryImages(Long accountId, String xyGoodsId, String cid, String toId,
                                    XianyuGoodsAutoDeliveryConfig deliveryConfig, boolean needHumanLikeDelay) {
        String imageUrlStr = deliveryConfig.getAutoDeliveryImageUrl();
        if (imageUrlStr == null || imageUrlStr.trim().isEmpty()) {
            return;
        }
        String[] imageUrls = imageUrlStr.split(",");
        for (int i = 0; i < imageUrls.length; i++) {
            try {
                String url = imageUrls[i].trim();
                if (url.isEmpty()) continue;
                if (i > 0) {
                    if (needHumanLikeDelay) {
                        HumanLikeDelayUtils.thinkingDelay();
                    } else {
                        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    }
                }
                boolean imgSuccess = webSocketService.sendImageMessage(accountId, cid, toId, url, 800, 800);
                if (imgSuccess) {
                    log.info("【账号{}】自动发货图片[{}/{}]发送成功: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                    sentMessageSaveService.saveManualImageReply(accountId, cid, toId, url, xyGoodsId);
                } else {
                    log.warn("【账号{}】自动发货图片[{}/{}]发送失败: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId);
                }
            } catch (Exception e) {
                log.error("【账号{}】自动发货图片[{}/{}]发送异常: xyGoodsId={}", accountId, i + 1, imageUrls.length, xyGoodsId, e);
            }
        }
    }

    private void executeAutoConfirmShipment(Long accountId, String orderId) {
        if (orderId == null || orderId.isEmpty()) {
            log.warn("【账号{}】订单ID为空，无法自动确认发货", accountId);
            return;
        }
        log.info("【账号{}】提交异步自动确认发货: orderId={}", accountId, orderId);
        new Thread(() -> {
            try {
                HumanLikeDelayUtils.longDelay();
                String result = orderService.confirmShipment(accountId, orderId);
                if (result != null) {
                    log.info("【账号{}】✅ 自动确认发货成功: orderId={}", accountId, orderId);
                    orderMapper.updateConfirmState(accountId, orderId);
                } else {
                    log.error("【账号{}】❌ 自动确认发货失败: orderId={}", accountId, orderId);
                }
            } catch (Exception e) {
                log.error("【账号{}】自动确认发货异常: orderId={}", accountId, orderId, e);
            }
        }).start();
    }

    private void updateRecordState(Long recordId, Integer state, String content, String failReason) {
        try {
            orderMapper.updateStateContentAndFailReason(recordId, state, content, failReason);
        } catch (Exception e) {
            log.error("更新订单状态失败: recordId={}, state={}", recordId, state, e);
        }
    }

    /** 发货内容取不齐时的失败原因文案 */
    private String deliveryContentFailMessage(int deliveryMode, int need, int got) {
        if (deliveryMode == 1) {
            return "未配置发货内容";
        }
        if (deliveryMode == 2) {
            return got == 0
                    ? "卡密库存不足，无可用卡密"
                    : "卡密库存不足，需要" + need + "张，仅剩" + got + "张";
        }
        return "未知的发货模式: " + deliveryMode;
    }

    /** 解析逗号分隔的发货图片URL配置 */
    private List<String> parseImageUrls(String imageUrlStr) {
        List<String> imageUrls = new ArrayList<>();
        if (imageUrlStr == null || imageUrlStr.trim().isEmpty()) {
            return imageUrls;
        }
        for (String url : imageUrlStr.split(",")) {
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) imageUrls.add(trimmed);
        }
        return imageUrls;
    }

    @Override
    public void updateAutoConfirmShipment(Long accountId, String xyGoodsId, Integer autoConfirmShipment) {
        XianyuGoodsAutoDeliveryConfig config = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(accountId, xyGoodsId);
        if (config == null) {
            config = new XianyuGoodsAutoDeliveryConfig();
            config.setXianyuAccountId(accountId);
            config.setXyGoodsId(xyGoodsId);
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.insert(config);
        } else {
            config.setAutoConfirmShipment(autoConfirmShipment);
            autoDeliveryConfigMapper.updateById(config);
        }
    }

    @Override
    public com.feijimiao.xianyuassistant.common.ResultObject<String> manualDelivery(Long xianyuAccountId, String orderId, String content) {
        try {
            if (orderId == null || orderId.isEmpty()) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单ID不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("发货内容不能为空");
            }

            XianyuGoodsOrder record = orderMapper.selectByAccountIdAndOrderId(xianyuAccountId, orderId);
            if (record == null) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单记录不存在");
            }

            String sId = record.getSid() != null ? record.getSid() : record.getBuyerUserId() + "@goofish";
            String cid = sId.replace("@goofish", "");
            String toId = cid;

            boolean success = webSocketService.sendMessage(xianyuAccountId, cid, toId, content);
            if (success) {
                updateRecordState(record.getId(), 1, content, null);
                sentMessageSaveService.saveAiAssistantReply(xianyuAccountId, cid, toId, content, record.getXyGoodsId());
                log.info("【账号{}】自定义发货成功: orderId={}", xianyuAccountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.success("自定义发货成功");
            } else {
                updateRecordState(record.getId(), -1, content, "消息发送失败");
                log.error("【账号{}】自定义发货失败: orderId={}", xianyuAccountId, orderId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("消息发送失败");
            }
        } catch (Exception e) {
            log.error("【账号{}】自定义发货异常: orderId={}", xianyuAccountId, orderId, e);
            return com.feijimiao.xianyuassistant.common.ResultObject.failed("自定义发货异常: " + e.getMessage());
        }
    }

    @Override
    public com.feijimiao.xianyuassistant.common.ResultObject<ManualExtractDeliveryRespDTO> manualExtractDelivery(
            Long xianyuAccountId, String orderId) {
        try {
            if (xianyuAccountId == null) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("闲鱼账号ID不能为空");
            }
            if (orderId == null || orderId.trim().isEmpty()) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单ID不能为空");
            }

            XianyuGoodsOrder record = orderMapper.selectByAccountIdAndOrderId(xianyuAccountId, orderId);
            if (record == null) {
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("订单记录不存在");
            }

            Long recordId = record.getId();
            String xyGoodsId = record.getXyGoodsId();
            log.info("【账号{}】手动提取发货内容: recordId={}, xyGoodsId={}, orderId={}", xianyuAccountId, recordId, xyGoodsId, orderId);

            // 人工兜底场景下Cookie大概率已失效，订单详情拿不到不能中断，改用本地记录降级。
            String warning = null;
            String orderSkuId = null;
            Integer buyNum = null;
            OrderDetailFetcher.OrderDetailInfo orderDetail = orderDetailFetcher.fetch(xianyuAccountId, xyGoodsId, orderId);
            if (orderDetail != null) {
                orderMapper.updateOrderDetail(recordId, orderDetail.buyerUserName, orderDetail.orderCreateTime,
                        orderDetail.paySuccessTime, orderDetail.consignTime, orderDetail.skuName,
                        orderDetail.goodsTitle, orderDetail.totalPrice, orderDetail.buyNum);
                orderSkuId = orderDetail.skuId;
                buyNum = orderDetail.buyNum;
            } else {
                warning = "获取订单详情失败(可能Cookie已过期)，已改用本地订单记录的规格与数量";
                log.warn("【账号{}】手动提取时获取订单详情失败，降级使用本地记录: orderId={}", xianyuAccountId, orderId);
                if (record.getSkuName() != null && !record.getSkuName().isEmpty()) {
                    orderSkuId = skuResolver.resolveSkuIdByText(xianyuAccountId, xyGoodsId, record.getSkuName());
                }
                buyNum = record.getBuyNum();
            }
            if (buyNum == null || buyNum <= 0) {
                buyNum = 1;
            }
            log.info("【账号{}】手动提取订单SKU: orderId={}, skuId={}, buyNum={}", xianyuAccountId, orderId, orderSkuId, buyNum);

            XianyuGoodsAutoDeliveryConfig deliveryConfig = null;
            if (orderSkuId != null && !orderSkuId.isEmpty()) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdAndSkuId(xianyuAccountId, xyGoodsId, orderSkuId);
            }
            if (deliveryConfig == null) {
                deliveryConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(xianyuAccountId, xyGoodsId);
            }
            if (deliveryConfig == null) {
                log.warn("【账号{}】商品无匹配的发货配置: xyGoodsId={}, skuId={}", xianyuAccountId, xyGoodsId, orderSkuId);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed("无匹配的发货配置");
            }

            int deliveryMode = deliveryConfig.getDeliveryMode() != null ? deliveryConfig.getDeliveryMode() : 1;
            boolean reused = deliveryMode == 2 && countKamiUsage(orderId) > 0;

            DeliveryContext ctx = DeliveryContext.builder()
                    .recordId(recordId)
                    .accountId(xianyuAccountId)
                    .xyGoodsId(xyGoodsId)
                    .sId(record.getSid())
                    .orderId(orderId)
                    .buyerUserName(record.getBuyerUserName())
                    .deliveryConfig(deliveryConfig)
                    .build();

            List<String> contents = deliveryStrategyResolver.resolveBatch(deliveryMode, ctx, buyNum);
            if (contents.size() < buyNum) {
                String failMsg = deliveryContentFailMessage(deliveryMode, buyNum, contents.size());
                log.warn("【账号{}】手动提取发货内容失败: orderId={}, {}", xianyuAccountId, orderId, failMsg);
                return com.feijimiao.xianyuassistant.common.ResultObject.failed(failMsg);
            }

            String content = String.join("\n", contents);
            updateRecordState(recordId, 1, content, null);
            orderMapper.updateDeliveryWay(recordId, 1);
            log.info("【账号{}】✅ 手动提取发货内容成功: recordId={}, deliveryMode={}, 份数={}, reused={}",
                    xianyuAccountId, recordId, deliveryMode, contents.size(), reused);

            XianyuGoodsAutoDeliveryConfig baseConfig = autoDeliveryConfigMapper.findByAccountIdAndGoodsIdNoSku(xianyuAccountId, xyGoodsId);
            boolean autoConfirm = (baseConfig != null && baseConfig.getAutoConfirmShipment() != null && baseConfig.getAutoConfirmShipment() == 1);
            if (autoConfirm) {
                log.info("【账号{}】手动提取后自动确认发货: orderId={}", xianyuAccountId, orderId);
                executeAutoConfirmShipment(xianyuAccountId, orderId);
            }

            ManualExtractDeliveryRespDTO respDTO = new ManualExtractDeliveryRespDTO();
            respDTO.setOrderId(orderId);
            respDTO.setDeliveryMode(deliveryMode);
            respDTO.setBuyNum(buyNum);
            respDTO.setKamiCount(contents.size());
            respDTO.setContent(content);
            respDTO.setContents(contents);
            respDTO.setImageUrls(parseImageUrls(deliveryConfig.getAutoDeliveryImageUrl()));
            respDTO.setConfirmShipmentTriggered(autoConfirm);
            respDTO.setReused(reused);
            respDTO.setWarning(warning);
            return com.feijimiao.xianyuassistant.common.ResultObject.success(respDTO);

        } catch (Exception e) {
            log.error("【账号{}】手动提取发货内容异常: orderId={}", xianyuAccountId, orderId, e);
            return com.feijimiao.xianyuassistant.common.ResultObject.failed("手动提取发货内容异常: " + e.getMessage());
        }
    }

    /** 统计该订单已有的卡密使用记录数，用于判断本次是否为复用已分配的卡密 */
    private long countKamiUsage(String orderId) {
        try {
            QueryWrapper<XianyuKamiUsageRecord> wrapper = new QueryWrapper<>();
            wrapper.eq("order_id", orderId);
            Long count = kamiUsageRecordMapper.selectCount(wrapper);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.warn("统计卡密使用记录失败: orderId={}", orderId, e);
            return 0L;
        }
    }
}
