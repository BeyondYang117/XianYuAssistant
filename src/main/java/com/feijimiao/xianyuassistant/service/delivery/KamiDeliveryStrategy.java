package com.feijimiao.xianyuassistant.service.delivery;

import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;
import com.feijimiao.xianyuassistant.entity.XianyuKamiUsageRecord;
import com.feijimiao.xianyuassistant.mapper.XianyuKamiUsageRecordMapper;
import com.feijimiao.xianyuassistant.service.KamiConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡密发货策略（deliveryMode=2）
 *
 * <p>从卡密仓库获取可用卡密，用模板替换 {kmKey} 占位符后返回发货内容。</p>
 *
 * <h3>流程：</h3>
 * <ol>
 *   <li>遍历绑定的卡密配置ID列表（逗号分隔）</li>
 *   <li>调用 {@link KamiConfigService#acquireKamiBatch} 获取未使用卡密，逐个配置补齐所需张数</li>
 *   <li>记录卡密使用记录</li>
 *   <li>用模板替换 {kmKey} 占位符</li>
 * </ol>
 */
@Slf4j
@Component
public class KamiDeliveryStrategy implements DeliveryContentStrategy {

    @Autowired
    private KamiConfigService kamiConfigService;

    @Autowired
    private XianyuKamiUsageRecordMapper kamiUsageRecordMapper;

    @Override
    public boolean supports(int deliveryMode) {
        return deliveryMode == 2;
    }

    @Override
    public List<String> resolveBatch(DeliveryContext context, int count) {
        List<String> contents = acquireKamiContents(
                context.getDeliveryConfig().getKamiConfigIds(),
                context.getDeliveryConfig().getKamiDeliveryTemplate(),
                context.getOrderId(),
                context.getAccountId(),
                context.getXyGoodsId(),
                context.getSId(),
                context.getBuyerUserName(),
                count
        );
        if (contents.isEmpty()) {
            log.warn("【账号{}】卡密发货模式下无可用卡密: xyGoodsId={}, kamiConfigIds={}",
                    context.getAccountId(), context.getXyGoodsId(), context.getDeliveryConfig().getKamiConfigIds());
            return contents;
        }
        log.info("【账号{}】卡密发货模式: 需要{}张, 实际获取{}张", context.getAccountId(), count, contents.size());
        return contents;
    }

    private List<String> acquireKamiContents(String kamiConfigIds, String kamiDeliveryTemplate,
                                             String orderId, Long accountId, String xyGoodsId, String sId,
                                             String buyerUserName, int count) {
        List<String> contents = new ArrayList<>();
        if (kamiConfigIds == null || kamiConfigIds.trim().isEmpty()) {
            log.warn("【账号{}】卡密发货未绑定卡密配置: xyGoodsId={}", accountId, xyGoodsId);
            return contents;
        }

        String[] configIdArr = kamiConfigIds.split(",");
        for (String configIdStr : configIdArr) {
            if (contents.size() >= count) {
                break;
            }
            try {
                Long configId = Long.parseLong(configIdStr.trim());
                int remaining = count - contents.size();
                List<XianyuKamiItem> kamiItems = kamiConfigService.acquireKamiBatch(configId, orderId, remaining);
                for (XianyuKamiItem kamiItem : kamiItems) {
                    XianyuKamiUsageRecord usageRecord = new XianyuKamiUsageRecord();
                    usageRecord.setKamiConfigId(configId);
                    usageRecord.setKamiItemId(kamiItem.getId());
                    usageRecord.setXianyuAccountId(accountId);
                    usageRecord.setXyGoodsId(xyGoodsId);
                    usageRecord.setOrderId(orderId);
                    usageRecord.setKamiContent(kamiItem.getKamiContent());
                    String cid = sId == null ? null : sId.replace("@goofish", "");
                    usageRecord.setBuyerUserId(cid);
                    usageRecord.setBuyerUserName(buyerUserName);
                    kamiUsageRecordMapper.insertIgnore(usageRecord);
                    log.info("【账号{}】卡密扣减成功: configId={}, itemId={}, orderId={}", accountId, configId, kamiItem.getId(), orderId);

                    String kamiContent = kamiItem.getKamiContent();
                    if (kamiDeliveryTemplate != null && !kamiDeliveryTemplate.trim().isEmpty()) {
                        kamiContent = kamiDeliveryTemplate.replace("{kmKey}", kamiContent);
                    }
                    contents.add(kamiContent);
                }
            } catch (NumberFormatException e) {
                log.warn("【账号{}】卡密配置ID格式错误: {}", accountId, configIdStr);
            }
        }
        return contents;
    }
}
