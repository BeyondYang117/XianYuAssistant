package com.feijimiao.xianyuassistant.controller.dto;

import lombok.Data;
import java.util.List;

@Data
public class UnreadMessagesRespDTO {
    private Integer unreadCount;
    private List<UnreadMessageDTO> messages;
}
