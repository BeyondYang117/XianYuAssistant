package com.feijimiao.xianyuassistant.mapper.projection;

import lombok.Data;

@Data
public class UnreadMessageRow {
    private Long accountId;
    private String sId;
    private String peerUserId;
    private String peerUserName;
    private String lastMessage;
    private Long lastMessageId;
    private Long lastMessageTime;
    private String xyGoodsId;
}
