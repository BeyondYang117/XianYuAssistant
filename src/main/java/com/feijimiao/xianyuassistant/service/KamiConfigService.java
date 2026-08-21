package com.feijimiao.xianyuassistant.service;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.*;
import com.feijimiao.xianyuassistant.entity.XianyuKamiConfig;
import com.feijimiao.xianyuassistant.entity.XianyuKamiItem;

import java.util.List;

public interface KamiConfigService {

    ResultObject<KamiConfigRespDTO> createOrUpdateConfig(KamiConfigReqDTO reqDTO);

    ResultObject<List<KamiConfigRespDTO>> getConfigsByAccountId(Long xianyuAccountId);

    ResultObject<KamiConfigRespDTO> getConfigById(Long id);

    ResultObject<Void> deleteConfig(Long id);

    ResultObject<KamiItemRespDTO> addKamiItem(KamiItemReqDTO reqDTO);

    ResultObject<Integer> batchImportKamiItems(KamiBatchImportReqDTO reqDTO);

    ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigId(Long kamiConfigId);

    ResultObject<List<KamiItemRespDTO>> getKamiItemsByConfigIdWithFilter(KamiItemQueryReqDTO reqDTO);

    ResultObject<Void> deleteKamiItem(Long id);

    ResultObject<Void> resetKamiItem(Long id);

    XianyuKamiItem acquireKami(Long kamiConfigId, String orderId);

    /**
     * 批量获取卡密（同一订单买多份时使用）
     *
     * <p>同一订单重复调用具备幂等性：已分配给该订单的卡密会被复用，不会重复扣减库存。</p>
     *
     * @param kamiConfigId 卡密配置ID
     * @param orderId      订单ID
     * @param count        需要的张数
     * @return 实际获取到的卡密列表，库存不足时长度可能小于 count，由调用方判定是否算失败
     */
    List<XianyuKamiItem> acquireKamiBatch(Long kamiConfigId, String orderId, int count);

    XianyuKamiConfig getConfig(Long kamiConfigId);

    ResultObject<List<KamiItemRespDTO>> exportKamiItems(KamiExportReqDTO reqDTO);
}
