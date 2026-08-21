package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuKamiUsageRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface XianyuKamiUsageRecordMapper extends BaseMapper<XianyuKamiUsageRecord> {

    @Insert("""
            INSERT OR IGNORE INTO xianyu_kami_usage_record
                (kami_config_id, kami_item_id, xianyu_account_id, xy_goods_id, order_id,
                 buyer_user_id, buyer_user_name, kami_content, create_time)
            VALUES
                (#{kamiConfigId}, #{kamiItemId}, #{xianyuAccountId}, #{xyGoodsId}, #{orderId},
                 #{buyerUserId}, #{buyerUserName}, #{kamiContent}, #{createTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnore(XianyuKamiUsageRecord record);
}
