package com.campusfix.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.message.dto.MessageDtos.NotificationPageVO;
import com.campusfix.message.dto.MessageDtos.NotificationVO;
import com.campusfix.message.entity.Notification;
import com.campusfix.message.mapper.NotificationMapper;
import com.campusfix.message.ws.WebSocketSessionRegistry;
import com.campusfix.order.entity.RepairOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 站内通知：落库 + WebSocket 实时推送。
 *
 * <p>通知只增不改（除已读），触发点集中在两处：</p>
 * <ol>
 *   <li>{@link #onTransition} —— 工单状态机每次成功流转后调用，
 *       按「基线 §11.4 通知触发点」把消息发给报修人 / 维修人（排除操作人本人）；</li>
 *   <li>{@link #notify} —— 聊天等其它业务直接调用。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TYPE_ORDER = "ORDER_STATUS";
    public static final String TYPE_CHAT = "CHAT";
    public static final String TYPE_SYSTEM = "SYSTEM";

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final NotificationMapper notificationMapper;
    private final WebSocketSessionRegistry wsRegistry;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------ 基础能力

    /** 写一条通知并尝试实时推送；任何失败都不影响调用方事务（推送在落库成功后进行）。 */
    public void notify(Long receiverId, String type, String title, String content, Long bizId) {
        if (receiverId == null || receiverId <= 0) {
            return;
        }
        Notification n = new Notification();
        n.setReceiverId(receiverId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content == null ? "" : content);
        n.setBizId(bizId);
        n.setReadFlag(0);
        notificationMapper.insert(n);

        wsRegistry.push(receiverId, toJson("NOTIFY", toVO(n)));
    }

    private void notifyExcluding(Long receiverId, Long operatorId, String type,
                                 String title, String content, Long bizId) {
        if (receiverId == null || receiverId.equals(operatorId)) {
            return;
        }
        notify(receiverId, type, title, content, bizId);
    }

    // ------------------------------------------------------------ 查询 / 已读

    public NotificationPageVO page(Long userId, int page, int size, String type, Boolean unread) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(StringUtils.hasText(type), Notification::getType, type)
                .eq(unread != null && unread, Notification::getReadFlag, 0)
                .orderByDesc(Notification::getId);
        IPage<Notification> result = notificationMapper.selectPage(
                new Page<>(page, Math.min(Math.max(size, 1), 50)), wrapper);
        long unreadCount = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getReadFlag, 0));
        PageResult<NotificationVO> vo = PageResult.of(result, this::toVO);
        return new NotificationPageVO(vo.getList(), vo.getTotal(), unreadCount);
    }

    public long unreadCount(Long userId) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    public void markRead(Long userId, Long id) {
        int rows = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
        if (rows == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "通知不存在或已读");
        }
    }

    public int markAllRead(Long userId) {
        return notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .eq(Notification::getReadFlag, 0)
                .set(Notification::getReadFlag, 1));
    }

    // ------------------------------------------------------------ 状态机触发点（基线 §11.4）

    /**
     * 工单流转后的通知分发。order 是流转前的快照——REWORK / RESCHEDULE_REJECT
     * 会清空 assignee_id，但通知恰恰要发给「原维修人」，所以必须用旧值。
     */
    public void onTransition(RepairOrder order, OrderAction action, OrderStatus to,
                             LoginUser operator, String remark, Map<String, Object> param) {
        try {
            dispatch(order, action, operator, remark, param);
        } catch (Exception e) {
            // 通知失败绝不影响主流程
            log.warn("工单通知写入失败 orderId={} action={}", order.getId(), action, e);
        }
    }

    private void dispatch(RepairOrder order, OrderAction action, LoginUser operator,
                          String remark, Map<String, Object> param) {
        Long operatorId = operator == null ? null : operator.getId();
        Long reporter = order.getReporterId();
        Long assignee = order.getAssigneeId();
        Long orderId = order.getId();
        String title = order.getTitle();
        String reason = remark == null || remark.isBlank() ? "" : "：" + remark;
        switch (action) {
            case REVIEW_PASS -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "报修审核通过", "你的报修「" + title + "」已通过审核，进入抢单池。", orderId);
            case REVIEW_REJECT -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "报修审核驳回", "你的报修「" + title + "」被驳回" + reason, orderId);
            case ASSIGN -> notifyExcluding(paramRepairer(order, param), operatorId, TYPE_ORDER,
                    "新的指派工单", "你有新的指派工单「" + title + "」，请及时接单。", orderId);
            case TRANSFER -> notifyExcluding(paramRepairer(order, param), operatorId, TYPE_ORDER,
                    "工单转派给你", "工单「" + title + "」已转派给你，请及时接单。", orderId);
            case GRAB -> {
                notifyExcluding(reporter, operatorId, TYPE_ORDER,
                        "报修已被接单", "你的工单「" + title + "」已被维修人员抢单接办。", orderId);
                notifyExcluding(assignee, operatorId, TYPE_ORDER,
                        "指派工单已被抢", "工单「" + title + "」已被他人抢单接办。", orderId);
            }
            case ACCEPT -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "维修人员已接单", "你的工单「" + title + "」已被接单，请留意预约时间。", orderId);
            case RETURN -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "维修人员退单", "工单「" + title + "」的维修人员已退单，工单回到抢单池。" + reason, orderId);
            case SCHEDULE_CONFIRM -> {
                Object plan = param == null ? null : param.get("planTime");
                String planText = plan instanceof java.time.LocalDateTime t ? "：" + t.format(TIME) : "";
                notifyExcluding(reporter, operatorId, TYPE_ORDER,
                        "预约已确认", "工单「" + title + "」的上门时间已确认" + planText + "。", orderId);
            }
            case RESCHEDULE_REJECT -> notifyExcluding(assignee, operatorId, TYPE_ORDER,
                    "改约被拒绝", "工单「" + title + "」的改约被报修人拒绝，工单已回到抢单池。", orderId);
            case SUSPEND -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "维修挂起", "工单「" + title + "」维修暂时挂起" + reason, orderId);
            case RESUME -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "维修恢复", "工单「" + title + "」已恢复维修。", orderId);
            case SUBMIT_RESULT -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "维修完成，待确认", "工单「" + title + "」维修已完成，请在截止时间前确认验收，超时将自动验收并扣除信誉分。", orderId);
            case CONFIRM -> notifyExcluding(assignee, operatorId, TYPE_ORDER,
                    "验收已通过", "工单「" + title + "」已由报修人确认完成。", orderId);
            case AUTO_CONFIRM -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "超时自动验收", "工单「" + title + "」超过验收截止时间，系统已自动验收并扣除信誉分。", orderId);
            case REWORK -> notifyExcluding(assignee, operatorId, TYPE_ORDER,
                    "工单退回返修", "工单「" + title + "」被报修人申请返修，将重新派单预约" + reason, orderId);
            case UNREPAIRABLE -> notifyExcluding(reporter, operatorId, TYPE_ORDER,
                    "无法维修", "工单「" + title + "」被判定无法维修" + reason, orderId);
            case CANCEL -> {
                notifyExcluding(reporter, operatorId, TYPE_ORDER,
                        "工单已取消", "工单「" + title + "」已被管理员取消" + reason, orderId);
                notifyExcluding(assignee, operatorId, TYPE_ORDER,
                        "工单已取消", "你承接的工单「" + title + "」已被管理员取消。", orderId);
            }
            default -> {
                // 其它动作暂无通知需求
            }
        }
    }

    // ------------------------------------------------------------ 内部工具

    /**
     * 指派/转派的目标维修人。order 是流转前快照，assigneeId 还是旧值（指派前为 null），
     * 必须从动作参数里取新维修人。
     */
    private Long paramRepairer(RepairOrder order, Map<String, Object> param) {
        Object id = param == null ? null : param.get("repairerId");
        if (id instanceof Number n) {
            return n.longValue();
        }
        return order.getAssigneeId();
    }

    private NotificationVO toVO(Notification n) {
        return new NotificationVO(n.getId(), n.getType(), n.getTitle(), n.getContent(),
                n.getBizId(), n.getReadFlag(), n.getCreatedAt());
    }

    /** 供 ChatService 复用：把业务事件包成 WS 帧推给指定用户。 */
    public void pushEvent(Long userId, String event, Object payload) {
        if (userId == null) {
            return;
        }
        wsRegistry.push(userId, toJson(event, payload));
    }

    private String toJson(String event, Object payload) {
        try {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", event);
            frame.put("payload", payload);
            return objectMapper.writeValueAsString(frame);
        } catch (Exception e) {
            log.warn("WS 帧序列化失败 event={}", event, e);
            return "{\"type\":\"" + event + "\"}";
        }
    }
}
