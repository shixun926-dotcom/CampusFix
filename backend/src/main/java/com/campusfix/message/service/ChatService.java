package com.campusfix.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.message.dto.MessageDtos.ChatMessageVO;
import com.campusfix.message.dto.MessageDtos.ChatSendRequest;
import com.campusfix.message.entity.ChatMessage;
import com.campusfix.message.mapper.ChatMessageMapper;
import com.campusfix.message.ws.WebSocketSessionRegistry;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.DeptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工单维度站内聊天（基线 §11.1）。
 *
 * <p>会话以工单为单位，报修人 ↔ 当前维修人一对一；后勤管理员/系统管理员作为
 * 相关方可查看全部消息并发送（发给当前对话另一方）。规则：</p>
 * <ul>
 *   <li>只有工单相关方能收发：报修人、当前维修人、（部门匹配的）后勤、管理员；</li>
 *   <li>报修人发送要求维修人已接单，否则无会话对象（3001）；</li>
 *   <li>内容 1-500 字符，一期纯文本，消息必须落库（离线可补）；</li>
 *   <li>拉取历史时把「发给我的未读」置为已读；同时给对方写一条 CHAT 通知。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** 单次最多返回的历史条数（老单保护，正常会话远达不到） */
    private static final int MAX_HISTORY = 200;

    private final ChatMessageMapper chatMapper;
    private final RepairOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final DeptService deptService;
    private final NotificationService notificationService;
    private final WebSocketSessionRegistry wsRegistry;

    // ------------------------------------------------------------------ 发送

    @Transactional
    public ChatMessageVO send(Long orderId, ChatSendRequest req, LoginUser me) {
        String content = req == null || req.content() == null ? "" : req.content().trim();
        if (content.isEmpty() || content.length() > 500) {
            throw new BizException(ResultCode.PARAM_INVALID, "消息内容需为 1-500 个字符");
        }
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工单不存在");
        }
        checkAccess(order, me);

        Long receiverId = resolveReceiver(order, me);

        ChatMessage msg = new ChatMessage();
        msg.setOrderId(orderId);
        msg.setSenderId(me.getId());
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setReadFlag(0);
        chatMapper.insert(msg);

        // 对方离线也可补看：通知落库 + 实时推送
        notificationService.notify(receiverId, NotificationService.TYPE_CHAT,
                "新聊天消息", me.getRealName() + "：" + content, orderId);

        ChatMessageVO vo = toVO(msg, Map.of(me.getId(), displayName(me)));
        // 双端都推：对方收消息，本人其他标签页同步
        notificationService.pushEvent(receiverId, "CHAT", vo);
        notificationService.pushEvent(me.getId(), "CHAT", vo);
        return vo;
    }

    // ------------------------------------------------------------------ 历史

    /** 拉取会话历史（升序），并把发给我的未读消息置为已读。 */
    @Transactional
    public List<ChatMessageVO> list(Long orderId, LoginUser me) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工单不存在");
        }
        checkAccess(order, me);

        List<ChatMessage> messages = chatMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getOrderId, orderId)
                .orderByAsc(ChatMessage::getId)
                .last("LIMIT " + MAX_HISTORY));

        // 我收到的未读 → 已读
        chatMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getOrderId, orderId)
                .eq(ChatMessage::getReceiverId, me.getId())
                .eq(ChatMessage::getReadFlag, 0)
                .set(ChatMessage::getReadFlag, 1));

        Map<Long, String> names = nameMap(messages);
        return messages.stream().map(m -> toVO(m, names)).toList();
    }

    /** 该工单是否有我未读的聊天消息（工单列表角标用）。 */
    public long unreadInOrder(Long orderId, Long meId) {
        Long count = chatMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getOrderId, orderId)
                .eq(ChatMessage::getReceiverId, meId)
                .eq(ChatMessage::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 相关方校验：报修人 / 当前维修人 / 管理员 / 本部门（含下级）的后勤管理员。
     */
    private void checkAccess(RepairOrder order, LoginUser me) {
        if (me.getId().equals(order.getReporterId()) || me.getId().equals(order.getAssigneeId())) {
            return;
        }
        if (me.hasRole(RoleCode.ADMIN)) {
            return;
        }
        if (me.hasRole(RoleCode.MANAGER) && me.getDeptId() != null && order.getDeptId() != null) {
            Set<Long> scope = deptService.getDescendantIds(me.getDeptId());
            if (scope.contains(order.getDeptId())) {
                return;
            }
        }
        throw new BizException(ResultCode.DATA_FORBIDDEN, "你不是该工单的相关方，无法使用会话");
    }

    /**
     * 收件人：报修人 ↔ 维修人互发；管理员/后勤发给当前对话的另一方。
     */
    private Long resolveReceiver(RepairOrder order, LoginUser me) {
        if (me.getId().equals(order.getReporterId())) {
            if (order.getAssigneeId() == null) {
                throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "维修人员接单后才能发起会话");
            }
            return order.getAssigneeId();
        }
        if (me.getId().equals(order.getAssigneeId())) {
            return order.getReporterId();
        }
        // 管理员 / 后勤：优先发给维修人，无维修人则发给报修人
        return order.getAssigneeId() != null ? order.getAssigneeId() : order.getReporterId();
    }

    private Map<Long, String> nameMap(List<ChatMessage> messages) {
        List<Long> ids = messages.stream().map(ChatMessage::getSenderId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, this::displayName,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String displayName(SysUser u) {
        return StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername();
    }

    private String displayName(LoginUser u) {
        return StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername();
    }

    private ChatMessageVO toVO(ChatMessage m, Map<Long, String> names) {
        return new ChatMessageVO(m.getId(), m.getOrderId(), m.getSenderId(),
                names.getOrDefault(m.getSenderId(), "用户" + m.getSenderId()),
                m.getReceiverId(), m.getContent(), m.getReadFlag(), m.getCreatedAt());
    }
}
