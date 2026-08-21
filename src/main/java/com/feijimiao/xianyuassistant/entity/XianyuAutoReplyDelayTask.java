package com.feijimiao.xianyuassistant.entity;

import lombok.Data;

@Data
public class XianyuAutoReplyDelayTask {
    private String taskKey;
    private Long xianyuAccountId;
    private String sId;
    private String messagesJson;
    private Long executeAt;
    private String updatedTime;
}
