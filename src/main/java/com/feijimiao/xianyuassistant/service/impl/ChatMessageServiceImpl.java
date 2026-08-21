package com.feijimiao.xianyuassistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuAccount;
import com.feijimiao.xianyuassistant.entity.XianyuChatMessage;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuChatMessageMapper;
import com.feijimiao.xianyuassistant.controller.dto.MsgContextReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgListReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgListRespDTO;
import com.feijimiao.xianyuassistant.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 聊天消息服务实现
 * 
 * <p>职责：提供消息查询相关的服务</p>
 * <p>注意：WebSocket 消息的解析和保存现在由 SyncMessageHandler 直接处理</p>
 */
@Slf4j
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final String IMAGE_PREFIX = "[图片]";
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private XianyuChatMessageMapper chatMessageMapper;
    
    @Autowired
    private XianyuAccountMapper accountMapper;
    
    @Override
    public List<XianyuChatMessage> getMessagesByAccountId(Long accountId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return chatMessageMapper.findByAccountId(accountId, pageSize, offset);
    }
    
    @Override
    public List<XianyuChatMessage> getMessagesBySessionId(String sessionId) {
        return chatMessageMapper.findBySId(sessionId);
    }
    
    @Override
    public ResultObject<MsgListRespDTO> getMessageList(MsgListReqDTO reqDTO) {
        try {
            // 参数验证
            if (reqDTO.getXianyuAccountId() == null) {
                return ResultObject.validateFailed("xianyuAccountId不能为空");
            }
            
            // 设置默认值
            int pageNum = reqDTO.getPageNum() != null && reqDTO.getPageNum() > 0 ? reqDTO.getPageNum() : 1;
            int pageSize = reqDTO.getPageSize() != null && reqDTO.getPageSize() > 0 ? reqDTO.getPageSize() : 20;
            
            // 计算偏移量
            int offset = (pageNum - 1) * pageSize;
            
            // 获取当前账号的UNB（用于过滤）
            String currentAccountUnb = null;
            if (reqDTO.getFilterCurrentAccount() != null && reqDTO.getFilterCurrentAccount()) {
                XianyuAccount account = accountMapper.selectById(reqDTO.getXianyuAccountId());
                if (account != null) {
                    currentAccountUnb = account.getUnb();
                }
            }
            
            // 查询总数
            int totalCount = chatMessageMapper.countMessages(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    currentAccountUnb
            );
            
            // 查询分页数据
            List<XianyuChatMessage> messages = chatMessageMapper.findMessagesByPage(
                    reqDTO.getXianyuAccountId(),
                    reqDTO.getXyGoodsId(),
                    currentAccountUnb,
                    pageSize,
                    offset
            );
            
            // 转换为DTO
            List<MsgDTO> msgDTOList = new ArrayList<>();
            if (messages != null) {
                for (XianyuChatMessage message : messages) {
                    msgDTOList.add(toMsgDTO(message));
                }
            }
            
            // 计算总页数
            int totalPage = (int) Math.ceil((double) totalCount / pageSize);
            if (totalPage == 0 && totalCount > 0) {
                totalPage = 1;
            }
            
            // 构建响应
            MsgListRespDTO respDTO = new MsgListRespDTO();
            respDTO.setList(msgDTOList);
            respDTO.setTotalCount(totalCount);
            respDTO.setPageNum(pageNum);
            respDTO.setPageSize(pageSize);
            respDTO.setTotalPage(totalPage);
            
            return ResultObject.success(respDTO);
            
        } catch (Exception e) {
            log.error("查询消息列表失败: accountId={}, xyGoodsId={}, filterCurrentAccount={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getFilterCurrentAccount(), e);
            return ResultObject.failed("查询消息列表失败: " + e.getMessage());
        }
    }
    
    @Override
    public ResultObject<?> getContextMessages(MsgContextReqDTO reqDTO) {
        try {
            if (reqDTO.getSid() == null || reqDTO.getSid().isEmpty()) {
                return ResultObject.validateFailed("sid不能为空");
            }
            
            int limit = reqDTO.getLimit() != null && reqDTO.getLimit() > 0 ? reqDTO.getLimit() : 20;
            int offset = reqDTO.getOffset() != null && reqDTO.getOffset() >= 0 ? reqDTO.getOffset() : 0;
            
            List<XianyuChatMessage> messages = chatMessageMapper.findRecentBySId(reqDTO.getSid(), limit, offset);
            
            List<MsgDTO> msgDTOList = new ArrayList<>();
            if (messages != null) {
                for (XianyuChatMessage message : messages) {
                    msgDTOList.add(toMsgDTO(message));
                }
            }
            
            return ResultObject.success(msgDTOList);
            
        } catch (Exception e) {
            log.error("查询上下文消息失败: sid={}", reqDTO.getSid(), e);
            return ResultObject.failed("查询上下文消息失败: " + e.getMessage());
        }
    }

    private MsgDTO toMsgDTO(XianyuChatMessage message) {
        MsgDTO msgDTO = new MsgDTO();
        msgDTO.setId(message.getId());
        msgDTO.setSId(message.getSId());
        msgDTO.setContentType(message.getContentType());
        msgDTO.setMsgContent(message.getMsgContent());
        msgDTO.setImageUrls(extractImageUrls(message));
        msgDTO.setXyGoodsId(message.getXyGoodsId());
        msgDTO.setReminderUrl(message.getReminderUrl());
        msgDTO.setSenderUserName(message.getSenderUserName());
        msgDTO.setSenderUserId(message.getSenderUserId());
        msgDTO.setMessageTime(message.getMessageTime());
        return msgDTO;
    }

    List<String> extractImageUrls(XianyuChatMessage message) {
        Set<String> imageUrls = new LinkedHashSet<>();
        String content = message.getMsgContent();
        if (content != null && content.startsWith(IMAGE_PREFIX)) {
            addImageUrl(imageUrls, content.substring(IMAGE_PREFIX.length()).trim());
        }

        if (message.getContentType() != null && message.getContentType() == 2
                && message.getCompleteMsg() != null && !message.getCompleteMsg().isBlank()) {
            try {
                collectImageUrls(objectMapper.readTree(message.getCompleteMsg()), imageUrls);
            } catch (Exception e) {
                log.debug("解析图片消息地址失败: messageId={}", message.getId(), e);
            }
        }
        return new ArrayList<>(imageUrls);
    }

    private void collectImageUrls(JsonNode node, Set<String> imageUrls) {
        if (node == null) return;

        if (node.isObject()) {
            if (node.path("contentType").asInt(-1) == 2) {
                JsonNode pics = node.path("image").path("pics");
                if (pics.isArray()) {
                    for (JsonNode pic : pics) {
                        // Incoming payloads have used both url and originalUrl.
                        addImageUrl(imageUrls, pic.path("url").asText());
                        addImageUrl(imageUrls, pic.path("originalUrl").asText());
                        addImageUrl(imageUrls, pic.path("originUrl").asText());
                    }
                }
            }
            node.elements().forEachRemaining(child -> collectImageUrls(child, imageUrls));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectImageUrls(child, imageUrls));
        } else if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.startsWith("{") || text.startsWith("[")) {
                try {
                    collectImageUrls(objectMapper.readTree(text), imageUrls);
                } catch (Exception ignored) {
                    // 普通聊天文本可能恰好以括号开头，不属于嵌套 JSON。
                }
            }
        }
    }

    private void addImageUrl(Set<String> imageUrls, String url) {
        if (url != null && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("//"))) {
            imageUrls.add(url);
        }
    }
}
