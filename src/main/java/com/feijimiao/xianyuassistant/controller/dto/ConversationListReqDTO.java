package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

/**
 * 会话工作台列表查询参数。
 */
@Data
public class ConversationListReqDTO {

    private Long xianyuAccountId;
    private String xyGoodsId;
    private String keyword;
    private Boolean needsReplyOnly = false;
    private Integer pageNum = 1;
    private Integer pageSize = 30;
}
