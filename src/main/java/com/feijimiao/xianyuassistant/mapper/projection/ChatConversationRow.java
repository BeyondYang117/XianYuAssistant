package com.feijimiao.xianyuassistant.mapper.projection;

import lombok.Data;

/**
 * 会话聚合查询结果。
 */
@Data
public class ChatConversationRow {

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
