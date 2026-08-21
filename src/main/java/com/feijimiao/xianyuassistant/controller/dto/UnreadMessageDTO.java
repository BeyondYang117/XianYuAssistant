package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;

@Data
public class UnreadMessageDTO {
    private Long accountId;
    private String sId;
    private String peerUserId;
    private String peerUserName;
    private String lastMessage;
    private Long lastMessageId;
    private Long lastMessageTime;
    private String xyGoodsId;
}
