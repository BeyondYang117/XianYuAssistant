package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

import java.util.List;

/**
 * 会话工作台分页响应。
 */
@Data
public class ConversationListRespDTO {

    private List<ConversationSummaryDTO> list;
    private Integer totalCount;
    private Integer totalPage;
    private Integer pageNum;
    private Integer pageSize;
}
