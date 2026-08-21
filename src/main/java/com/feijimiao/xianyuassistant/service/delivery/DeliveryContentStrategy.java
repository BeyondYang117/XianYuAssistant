package com.feijimiao.xianyuassistant.service.delivery;

import java.util.List;

/**
 * 发货内容解析策略接口
 *
 * <p>使用策略模式将发货模式（文本/卡密）拆分为独立实现，
 * 避免在主服务类中使用 if-else 分支。</p>
 *
 * <h3>两种实现：</h3>
 * <ul>
 *   <li>{@link TextDeliveryStrategy} - 文本发货（deliveryMode=1）：直接返回配置的文本内容</li>
 *   <li>{@link KamiDeliveryStrategy} - 卡密发货（deliveryMode=2）：从卡密仓库获取可用卡密，替换模板占位符</li>
 * </ul>
 */
public interface DeliveryContentStrategy {

    /**
     * 判断是否支持该发货模式
     *
     * @param deliveryMode 发货模式（1=文本，2=卡密）
     * @return true=支持
     */
    boolean supports(int deliveryMode);

    /**
     * 解析 count 份发货内容（订单买多份时一次性取齐，避免重复发同一张卡密）
     *
     * @param context 发货上下文（包含账号、商品、订单、配置等信息）
     * @param count   需要的份数
     * @return 发货内容列表；空列表表示无法发货，长度小于 count 表示库存不足，由调用方判定
     */
    List<String> resolveBatch(DeliveryContext context, int count);

    /**
     * 解析单份发货内容
     *
     * @param context 发货上下文（包含账号、商品、订单、配置等信息）
     * @return 发货内容文本，null表示无法发货（应中断发货流程）
     */
    default String resolve(DeliveryContext context) {
        List<String> contents = resolveBatch(context, 1);
        return (contents == null || contents.isEmpty()) ? null : contents.get(0);
    }
}
