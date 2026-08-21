package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.controller.dto.MsgContextReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgListReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.MsgListRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.ConversationListReqDTO;
import com.feijimiao.xianyuassistant.controller.dto.ConversationListRespDTO;
import com.feijimiao.xianyuassistant.controller.dto.UnreadMessagesReqDTO;
import com.feijimiao.xianyuassistant.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 消息管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/msg")
@CrossOrigin(origins = "*")
public class MsgController {

    @Autowired
    private ChatMessageService chatMessageService;

    /**
     * 分页查询消息列表
     * 按时间排序，时间新的在前面
     *
     * @param reqDTO 请求参数
     * @return 消息列表
     */
    @PostMapping("/list")
    public ResultObject<MsgListRespDTO> getMessageList(@RequestBody MsgListReqDTO reqDTO) {
        try {
            log.info("查询消息列表请求: xianyuAccountId={}, xyGoodsId={}, filterCurrentAccount={}, pageNum={}, pageSize={}",
                    reqDTO.getXianyuAccountId(), reqDTO.getXyGoodsId(), reqDTO.getFilterCurrentAccount(), reqDTO.getPageNum(), reqDTO.getPageSize());
            return chatMessageService.getMessageList(reqDTO);
        } catch (Exception e) {
            log.error("查询消息列表失败", e);
            return ResultObject.failed("查询消息列表失败: " + e.getMessage());
        }
    }

    /**
     * 按会话分页查询消息工作台。
     */
    @PostMapping("/conversations")
    public ResultObject<ConversationListRespDTO> getConversationList(@RequestBody ConversationListReqDTO reqDTO) {
        try {
            return chatMessageService.getConversationList(reqDTO);
        } catch (Exception e) {
            log.error("查询会话列表失败", e);
            return ResultObject.failed("查询会话列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据会话ID获取上下文消息（最近50条）
     *
     * @param reqDTO 请求参数（sid, limit）
     * @return 消息列表
     */
    @PostMapping("/context")
    public ResultObject<?> getContextMessages(@RequestBody MsgContextReqDTO reqDTO) {
        try {
            log.info("查询上下文消息请求: sid={}, limit={}", reqDTO.getSid(), reqDTO.getLimit());
            return chatMessageService.getContextMessages(reqDTO);
        } catch (Exception e) {
            log.error("查询上下文消息失败", e);
            return ResultObject.failed("查询上下文消息失败: " + e.getMessage());
        }
    }

    @PostMapping("/unread")
    public ResultObject<?> getUnreadMessages(@RequestBody UnreadMessagesReqDTO reqDTO) {
        return chatMessageService.getUnreadMessages(reqDTO == null ? null : reqDTO.getXianyuAccountId(), 50);
    }

    @PostMapping("/read")
    public ResultObject<?> markConversationRead(@RequestBody MsgContextReqDTO reqDTO) {
        return chatMessageService.markConversationRead(reqDTO);
    }
}
