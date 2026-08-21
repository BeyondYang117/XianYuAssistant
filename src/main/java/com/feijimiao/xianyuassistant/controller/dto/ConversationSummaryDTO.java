package com.feijimiao.xianyuassistant.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 消息工作台中的一条会话摘要。
 */
@Data
public class ConversationSummaryDTO {

    @JsonProperty("sid")
    private String sId;
    private String peerUserId;
    private String peerUserName;
    private String xyGoodsId;
    private Long lastMessageId;
    private Integer lastContentType;
    private String lastMessage;
    private Long lastMessageTime;
    private String lastSenderUserId;
    private Integer messageCount;
    private Boolean needsReply;
}
