package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.config.OrderProperties;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.message.service.NotificationService;
import com.campusfix.order.entity.OrderStallAlert;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.mapper.OrderStallAlertMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 状态滞留巡检（对应《系统设计说明书》§9.2 风险 R1「工单状态卡死」的应对措施）。
 *
 * <p>扫描「最后更新时间超过阈值、仍停留在非终态」的工单，将清单推送给管理员，
 * 提醒人工介入，避免工单无声卡死无人发现。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li><b>时间口径</b>：{@code updated_at}（每次流转由审计填充的最后活动时间），
 *       不是 created_at——已挂起/处理中的单可能持续有动作，不能算滞留。</li>
 *   <li><b>非终态集合</b>：{@link OrderStatus#activeStatuses()}（7 个非终态）。</li>
 *   <li><b>去重</b>：{@code order_stall_alert(order_id, status)} 唯一键，同单同状态只推一次；
 *       状态变化后再次滞留会重新推送（期望行为）。</li>
 *   <li><b>接收范围</b>：系统管理员（ADMIN）全员 + 后勤管理员（MANAGER）全员。
 *       工单不按部门拆分给 MANAGER，因为滞留工单需要有人兜底处理，
 *       且 MANAGER 数量少、按部门分片反而可能漏发。</li>
 *   <li><b>渠道</b>：站内信（type=SYSTEM，落库 + WebSocket 实时推送）。项目一期明确不做短信/邮件。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StallAlertService {

    private final RepairOrderMapper orderMapper;
    private final OrderStallAlertMapper stallAlertMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final OrderProperties orderProperties;

    /**
     * 执行一次滞留扫描并推送。返回本次实际推送的滞留工单数。
     *
     * <p>幂等：同一工单同一状态已推送过则跳过（唯一索引 + 先查后插双保险）。</p>
     */
    @Transactional
    public int scan() {
        int thresholdDays = Math.max(1, orderProperties.getStallAlertDays());
        LocalDateTime stallBefore = LocalDateTime.now().minusDays(thresholdDays);

        List<String> activeStatuses = OrderStatus.activeStatuses().stream()
                .map(OrderStatus::name).toList();
        List<RepairOrder> candidates = orderMapper.selectStallCandidates(activeStatuses, stallBefore);

        log.info("[StallAlert] 滞留扫描：阈值 {} 天，非终态 {} 种，候选 {} 张",
                thresholdDays, activeStatuses.size(), candidates.size());
        if (candidates.isEmpty()) {
            return 0;
        }

        // 过滤掉已推送过的（同单同状态）
        List<RepairOrder> toNotify = filterNotified(candidates);

        if (toNotify.isEmpty()) {
            log.info("[StallAlert] 滞留扫描：候选 {} 张均已在当前状态推送过，跳过", candidates.size());
            return 0;
        }

        // 接收人：全部启用中的 ADMIN + MANAGER
        List<SysUser> receivers = userMapper.selectEnabledByRoles(
                List.of(RoleCode.ADMIN, RoleCode.MANAGER));
        if (receivers.isEmpty()) {
            log.warn("[StallAlert] 没有启用的管理员账号，本次 {} 张滞留工单无法推送", toNotify.size());
            return 0;
        }

        int pushed = 0;
        for (RepairOrder order : toNotify) {
            try {
                notifyAdmins(order, receivers, thresholdDays);
                markNotified(order, stallBefore);
                pushed++;
            } catch (DuplicateKeyException e) {
                // 并发 / 前次部分成功兜底：唯一键冲突说明已推过，跳过
                log.info("[StallAlert] 工单 {} 状态 {} 已推送过（唯一键冲突），跳过",
                        order.getId(), order.getStatus());
            }
        }
        log.info("[StallAlert] 滞留巡检完成，向 {} 名管理员推送 {} 张滞留工单",
                receivers.size(), pushed);
        return pushed;
    }

    /** 查询当前已推送集合，过滤掉同单同状态已推过的工单。 */
    private List<RepairOrder> filterNotified(List<RepairOrder> candidates) {
        Set<String> notified = new HashSet<>();
        // 分页拉取已推送记录（数据量小，直接全量）
        List<OrderStallAlert> records = stallAlertMapper.selectList(
                new LambdaQueryWrapper<OrderStallAlert>()
                        .select(OrderStallAlert::getOrderId, OrderStallAlert::getStatus));
        for (OrderStallAlert r : records) {
            notified.add(r.getOrderId() + ":" + r.getStatus());
        }
        return candidates.stream()
                .filter(o -> !notified.contains(o.getId() + ":" + o.getStatus()))
                .toList();
    }

    private void notifyAdmins(RepairOrder order, List<SysUser> receivers, int thresholdDays) {
        String title = "工单滞留提醒";
        String content = "工单「" + order.getTitle() + "」（" + order.getOrderNo() + "）已停留在「"
                + OrderStatus.of(order.getStatus()).getLabel() + "」状态超过 " + thresholdDays
                + " 天，请及时跟进处理。";
        for (SysUser u : receivers) {
            // 站内信：type=SYSTEM，bizId=工单ID，前端点击可跳转工单详情
            notificationService.notify(u.getId(), NotificationService.TYPE_SYSTEM,
                    title, content, order.getId());
        }
    }

    private void markNotified(RepairOrder order, LocalDateTime stallSince) {
        OrderStallAlert record = new OrderStallAlert();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setStatus(order.getStatus());
        record.setStallSince(order.getUpdatedAt() == null ? stallSince : order.getUpdatedAt());
        record.setNotifiedCount(1);
        record.setLastNotifiedAt(LocalDateTime.now());
        stallAlertMapper.insert(record);
    }
}
