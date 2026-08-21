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
import com.feijimiao.xianyuassistant.controller.dto.ConversationListReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.ConversationListRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.ConversationSummaryDTO;
import com.feijimiao.xianyuassistant.controller.dto.UnreadMessageDTO;
import com.feijimiao.xianyuassistant.controller.dto.UnreadMessagesRespDTO;
import com.feijimiao.xianyuassistant.mapper.projection.ChatConversationRow;
import com.feijimiao.xianyuassistant.mapper.projection.UnreadMessageRow;
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

    @Override
    public ResultObject<ConversationListRespDTO> getConversationList(ConversationListReqDTO reqDTO) {
        if (reqDTO.getXianyuAccountId() == null) {
            return ResultObject.validateFailed("xianyuAccountId不能为空");
        }

        int pageNum = reqDTO.getPageNum() != null && reqDTO.getPageNum() > 0 ? reqDTO.getPageNum() : 1;
        int pageSize = reqDTO.getPageSize() != null && reqDTO.getPageSize() > 0
                ? Math.min(reqDTO.getPageSize(), 100) : 30;
        int offset = (pageNum - 1) * pageSize;
        boolean needsReplyOnly = Boolean.TRUE.equals(reqDTO.getNeedsReplyOnly());
        String keyword = reqDTO.getKeyword() == null ? null : reqDTO.getKeyword().trim();

        XianyuAccount account = accountMapper.selectById(reqDTO.getXianyuAccountId());
        String currentAccountUnb = account != null && account.getUnb() != null ? account.getUnb() : "";

        int totalCount = chatMessageMapper.countConversations(
                reqDTO.getXianyuAccountId(), currentAccountUnb, reqDTO.getXyGoodsId(), keyword, needsReplyOnly);
        List<ChatConversationRow> rows = chatMessageMapper.findConversationsByPage(
                reqDTO.getXianyuAccountId(), currentAccountUnb, reqDTO.getXyGoodsId(), keyword,
                needsReplyOnly, pageSize, offset);

        List<ConversationSummaryDTO> conversations = rows.stream().map(this::toConversationSummary).toList();
        ConversationListRespDTO response = new ConversationListRespDTO();
        response.setList(conversations);
        response.setTotalCount(totalCount);
        response.setPageNum(pageNum);
        response.setPageSize(pageSize);
        response.setTotalPage((int) Math.ceil((double) totalCount / pageSize));
        return ResultObject.success(response);
    }

    @Override
    public ResultObject<UnreadMessagesRespDTO> getUnreadMessages(Long accountId, int limit) {
        if (accountId == null) return ResultObject.validateFailed("xianyuAccountId不能为空");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        UnreadMessagesRespDTO response = new UnreadMessagesRespDTO();
        response.setUnreadCount(chatMessageMapper.countUnread(accountId));
        List<UnreadMessageDTO> messages = chatMessageMapper.findUnread(accountId, safeLimit).stream().map(row -> {
            UnreadMessageDTO dto = new UnreadMessageDTO();
            dto.setAccountId(row.getAccountId());
            dto.setSId(row.getSId());
            dto.setPeerUserId(row.getPeerUserId());
            dto.setPeerUserName(row.getPeerUserName());
            dto.setLastMessage(row.getLastMessage());
            dto.setLastMessageId(row.getLastMessageId());
            dto.setLastMessageTime(row.getLastMessageTime());
            dto.setXyGoodsId(row.getXyGoodsId());
            return dto;
        }).toList();
        response.setMessages(messages);
        return ResultObject.success(response);
    }

    @Override
    public ResultObject<?> markConversationRead(MsgContextReqDTO reqDTO) {
        if (reqDTO == null || reqDTO.getXianyuAccountId() == null || reqDTO.getSid() == null || reqDTO.getSid().isBlank()) {
            return ResultObject.validateFailed("xianyuAccountId和sid不能为空");
        }
        chatMessageMapper.markRead(reqDTO.getXianyuAccountId(), reqDTO.getSid());
        return ResultObject.success(null);
    }

    ConversationSummaryDTO toConversationSummary(ChatConversationRow row) {
        ConversationSummaryDTO dto = new ConversationSummaryDTO();
        dto.setSId(row.getSId());
        dto.setPeerUserId(row.getPeerUserId());
        dto.setPeerUserName(row.getPeerUserName());
        dto.setXyGoodsId(row.getXyGoodsId());
        dto.setLastMessageId(row.getLastMessageId());
        dto.setLastContentType(row.getLastContentType());
        dto.setLastMessage(row.getLastMessage());
        dto.setLastMessageTime(row.getLastMessageTime());
        dto.setLastSenderUserId(row.getLastSenderUserId());
        dto.setMessageCount(row.getMessageCount());
        dto.setNeedsReply(Boolean.TRUE.equals(row.getNeedsReply()));
        return dto;
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

        if (message.getCompleteMsg() != null && !message.getCompleteMsg().isBlank()) {
            try {
                collectImageUrls(objectMapper.readTree(message.getCompleteMsg()), imageUrls);
            } catch (Exception e) {
                log.debug("解析图片消息地址失败: messageId={}", message.getId(), e);
            }
        }
        return new ArrayList<>(imageUrls);
    }

    private void collectImageUrls(JsonNode node, Set<String> imageUrls) {
        collectImageUrls(node, imageUrls, false);
    }

    /**
     * 图片报文在不同客户端版本中会使用 image.pics、picUrl、imageUrl，
     * 有时还会再次编码成 JSON 字符串。只在图片字段上下文内收集 URL，
     * 避免把 reminderUrl 等业务链接误判为图片。
     */
    private void collectImageUrls(JsonNode node, Set<String> imageUrls, boolean imageContext) {
        if (node == null) return;

        if (node.isObject()) {
            boolean currentImageContext = imageContext || node.path("contentType").asInt(-1) == 2;
            node.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey().toLowerCase();
                JsonNode child = entry.getValue();
                boolean childImageContext = currentImageContext || isImageField(fieldName);
                if (child.isTextual() && childImageContext) {
                    addImageUrl(imageUrls, child.asText());
                }
                collectImageUrls(child, imageUrls, childImageContext);
            });
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> collectImageUrls(child, imageUrls, imageContext));
        } else if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.startsWith("{") || text.startsWith("[")) {
                try {
                    collectImageUrls(objectMapper.readTree(text), imageUrls, imageContext);
                } catch (Exception ignored) {
                    // 普通聊天文本可能恰好以括号开头，不属于嵌套 JSON。
                }
            } else if (imageContext) {
                addImageUrl(imageUrls, text);
            }
        }
    }

    private boolean isImageField(String fieldName) {
        return fieldName.contains("image")
                || fieldName.contains("pic")
                || fieldName.contains("photo")
                || fieldName.contains("media");
    }

    private void addImageUrl(Set<String> imageUrls, String url) {
        if (url == null) return;
        String normalizedUrl = url.trim();
        if (normalizedUrl.startsWith("https://") || normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("//")) {
            imageUrls.add(normalizedUrl);
        }
    }
}
