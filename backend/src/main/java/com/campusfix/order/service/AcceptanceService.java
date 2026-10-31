package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.config.CreditProperties;
import com.campusfix.common.config.OrderProperties;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.enums.ScheduleStatus;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.AcceptanceDtos;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.entity.OrderExtensionRequest;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairSchedule;
import com.campusfix.order.mapper.OrderExtensionRequestMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.mapper.RepairScheduleMapper;
import com.campusfix.order.statemachine.OrderStateMachine;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 确认验收：报修人确认完成、申请返修、延期申请与审批、超时自动验收。
 *
 * <p>三条规则来自基线：</p>
 * <ul>
 *   <li>确认完成只认「本单报修人」，不认角色 —— 管理员也可能给自己办公室报修；</li>
 *   <li>返修按方案 B 退回抢单池，同时作废旧预约、清空维修人，但<b>不扣维修工信誉分</b>；</li>
 *   <li>超期未确认且未获延期批准，自动验收并扣报修人信誉分。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptanceService {

    private final RepairOrderMapper orderMapper;
    private final RepairScheduleMapper scheduleMapper;
    private final OrderExtensionRequestMapper extensionMapper;
    private final SysUserMapper userMapper;
    private final OrderStateMachine stateMachine;
    private final OrderQueryService queryService;
    private final UserService userService;
    private final OrderProperties orderProperties;
    private final CreditProperties creditProperties;

    // ------------------------------------------------------------------ 确认 / 返修

    /** 报修人确认完成：待确认 → 已完成，解锁评价。 */
    @Transactional
    public OrderDtos.FireResult confirm(Long orderId, String remark, LoginUser me) {
        OrderStatus to = stateMachine.fire(orderId, OrderAction.CONFIRM, me,
                StringUtils.hasText(remark) ? remark : "报修人确认完成",
                Map.of());
        log.info("报修人确认完成 orderId={} operator={}", orderId, me.getId());
        return new OrderDtos.FireResult(orderId, to.name(), to.getLabel());
    }

    /**
     * 报修人申请返修：待确认 → 待处理。
     *
     * <p>状态机负责清空维修人、返修计数与验收截止时间；这里补两件它管不到的事：
     * 作废全部预约时段（需要重新预约）、记录返修原因到流转日志。</p>
     */
    @Transactional
    public OrderDtos.FireResult rework(Long orderId, String reason, LoginUser me) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.PARAM_INVALID, "请说明返修原因");
        }
        OrderStatus to = stateMachine.fire(orderId, OrderAction.REWORK, me,
                "申请返修：" + reason.trim(), Map.of());

        // 旧的预约一律作废，返修后要重新走「给时段 → 选择 → 确认」
        int cancelled = scheduleMapper.update(null, new LambdaUpdateWrapper<RepairSchedule>()
                .eq(RepairSchedule::getOrderId, orderId)
                .ne(RepairSchedule::getStatus, ScheduleStatus.CANCELLED.name())
                .set(RepairSchedule::getStatus, ScheduleStatus.CANCELLED.name())
                .set(RepairSchedule::getRemark, "工单返修，原预约作废"));

        log.info("报修人申请返修 orderId={} operator={} 作废时段={}", orderId, me.getId(), cancelled);
        return new OrderDtos.FireResult(orderId, to.name(), to.getLabel());
    }

    // ------------------------------------------------------------------ 延期

    /** 报修人申请延期确认：暂停自动验收倒计时，等管理员审批。 */
    @Transactional
    public AcceptanceDtos.ExtensionVO applyExtension(Long orderId, String reason, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getReporterId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有报修人可以申请延期");
        }
        if (!order.isStatus(OrderStatus.PENDING_CONFIRM)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "只有「待确认」状态可以申请延期");
        }
        Long pending = extensionMapper.selectCount(new LambdaQueryWrapper<OrderExtensionRequest>()
                .eq(OrderExtensionRequest::getOrderId, orderId)
                .eq(OrderExtensionRequest::getStatus, "PENDING"));
        if (pending != null && pending > 0) {
            throw new BizException(ResultCode.EXTENSION_EXISTS);
        }

        OrderExtensionRequest req = new OrderExtensionRequest();
        req.setOrderId(orderId);
        req.setStudentId(me.getId());
        req.setReason(reason.trim());
        req.setStatus("PENDING");
        extensionMapper.insert(req);
        log.info("报修人申请延期 orderId={} requestId={}", orderId, req.getId());
        return toVO(extensionMapper.selectById(req.getId()));
    }

    /** 管理员审批延期：通过则顺延 {@code confirm_deadline}，驳回则照常按超时处理。 */
    @Transactional
    public AcceptanceDtos.ExtensionVO handleExtension(Long extensionId,
                                                      AcceptanceDtos.HandleExtensionRequest body,
                                                      LoginUser me) {
        if (!me.hasRole(RoleCode.MANAGER) && !me.hasRole(RoleCode.ADMIN)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有管理员可以审批延期");
        }
        OrderExtensionRequest req = extensionMapper.selectById(extensionId);
        if (req == null) {
            throw new BizException(ResultCode.NOT_FOUND, "延期申请不存在");
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "该申请已处理");
        }

        boolean approve = Boolean.TRUE.equals(body.approve());
        int extraDays = body.extraDays() == null ? orderProperties.getExtensionDays() : body.extraDays();
        if (approve && (extraDays < 1 || extraDays > 30)) {
            throw new BizException(ResultCode.PARAM_INVALID, "顺延天数应在 1-30 天之间");
        }

        req.setStatus(approve ? "APPROVED" : "REJECTED");
        req.setHandlerId(me.getId());
        req.setHandleResult(StringUtils.hasText(body.result())
                ? body.result().trim()
                : (approve ? "同意延期 " + extraDays + " 天" : "不同意延期"));
        req.setHandledAt(LocalDateTime.now());
        extensionMapper.updateById(req);

        if (approve) {
            RepairOrder order = queryService.getOrder(req.getOrderId());
            if (order.isStatus(OrderStatus.PENDING_CONFIRM)) {
                LocalDateTime base = order.getConfirmDeadline() == null
                        ? LocalDateTime.now() : order.getConfirmDeadline();
                orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                        .eq(RepairOrder::getId, order.getId())
                        .eq(RepairOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                        .set(RepairOrder::getConfirmDeadline, base.plusDays(extraDays)));
            }
        }
        log.info("延期审批 extensionId={} approve={} handler={}", extensionId, approve, me.getId());
        return toVO(extensionMapper.selectById(extensionId));
    }

    // ------------------------------------------------------------------ 概况

    public AcceptanceDtos.AcceptanceVO acceptanceInfo(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!queryService.canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单的验收信息");
        }
        AcceptanceDtos.AcceptanceVO vo = new AcceptanceDtos.AcceptanceVO();
        vo.setOrderId(orderId);
        vo.setStatus(order.getStatus());
        vo.setStatusLabel(order.statusEnum().getLabel());
        vo.setConfirmDeadline(order.getConfirmDeadline());
        vo.setAutoConfirmed(order.getAutoConfirmed() != null && order.getAutoConfirmed() == 1);
        vo.setAutoConfirmDays(orderProperties.getAutoConfirmDays());
        vo.setExtensionDays(orderProperties.getExtensionDays());

        if (order.getConfirmDeadline() != null) {
            long hours = Duration.between(LocalDateTime.now(), order.getConfirmDeadline()).toHours();
            vo.setHoursLeft(hours);
            vo.setOverdue(hours < 0);
        }

        List<OrderExtensionRequest> list = extensionMapper.selectList(
                new LambdaQueryWrapper<OrderExtensionRequest>()
                        .eq(OrderExtensionRequest::getOrderId, orderId)
                        .orderByDesc(OrderExtensionRequest::getId));
        vo.setExtensions(list.stream().map(this::toVO).toList());
        vo.setHasPendingExtension(list.stream().anyMatch(e -> "PENDING".equals(e.getStatus())));
        return vo;
    }

    // ------------------------------------------------------------------ 自动验收

    /**
     * 超时自动验收：扫描 {@code confirm_deadline} 已过期的「待确认」工单，
     * 置为已完成并扣报修人信誉分。
     *
     * <p>逐单独立处理，单笔失败不影响其它单。返回本次处理数量。</p>
     */
    public int autoConfirm() {
        LocalDateTime now = LocalDateTime.now();
        List<RepairOrder> due = orderMapper.selectList(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                .isNotNull(RepairOrder::getConfirmDeadline)
                .le(RepairOrder::getConfirmDeadline, now)
                .last("LIMIT 200"));
        if (due.isEmpty()) {
            return 0;
        }
        log.info("自动验收扫描：截止时间已过的待确认工单 {} 张", due.size());

        int done = 0;
        for (RepairOrder order : due) {
            try {
                stateMachine.fire(order.getId(), OrderAction.AUTO_CONFIRM, null,
                        "超过 " + orderProperties.getAutoConfirmDays() + " 天未确认，系统自动验收",
                        Map.of());
                if (order.getReporterId() != null) {
                    userService.adjustCredit(order.getReporterId(), -creditProperties.getDeductScore(),
                            "超时未确认，系统自动验收（工单 " + order.getOrderNo() + "）",
                            order.getId(), null);
                }
                done++;
                log.info("自动验收 orderId={} 扣信誉分={}", order.getId(), creditProperties.getDeductScore());
            } catch (BizException e) {
                // 期间被学生确认 / 申请返修，跳过
                log.info("自动验收跳过 orderId={} reason={}", order.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("自动验收失败 orderId={}", order.getId(), e);
            }
        }
        return done;
    }

    // ------------------------------------------------------------------ 内部

    private AcceptanceDtos.ExtensionVO toVO(OrderExtensionRequest e) {
        AcceptanceDtos.ExtensionVO vo = new AcceptanceDtos.ExtensionVO();
        vo.setId(e.getId());
        vo.setOrderId(e.getOrderId());
        vo.setStudentId(e.getStudentId());
        vo.setReason(e.getReason());
        vo.setStatus(e.getStatus());
        vo.setStatusLabel(labelOf(e.getStatus()));
        vo.setHandlerId(e.getHandlerId());
        vo.setHandleResult(e.getHandleResult());
        vo.setHandledAt(e.getHandledAt());
        vo.setCreatedAt(e.getCreatedAt());

        List<Long> ids = java.util.stream.Stream.of(e.getStudentId(), e.getHandlerId())
                .filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            Map<Long, String> names = userMapper.selectBatchIds(ids).stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
            vo.setStudentName(names.get(e.getStudentId()));
            vo.setHandlerName(names.get(e.getHandlerId()));
        }
        return vo;
    }

    private static String labelOf(String status) {
        return switch (status) {
            case "PENDING" -> "待审批";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已驳回";
            default -> status;
        };
    }
}
