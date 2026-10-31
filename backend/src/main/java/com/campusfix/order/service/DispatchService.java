package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.config.CreditProperties;
import com.campusfix.common.config.OrderProperties;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairSchedule;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.mapper.RepairScheduleMapper;
import com.campusfix.order.statemachine.OrderStateMachine;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.service.DeptService;
import com.campusfix.system.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 派单分配：管理员指派 / 转派、维修工抢单 / 接单 / 退单、超时自动派单。
 *
 * <p>并发策略（ADR-04）：不引入分布式锁，靠 {@code version} 乐观锁做 CAS，
 * 更新影响行数为 0 即视为被他人抢先，返回 409。数据库行锁本身就是原子保障。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final RepairOrderMapper orderMapper;
    private final RepairScheduleMapper scheduleMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final DeptService deptService;
    private final UserService userService;
    private final OrderStateMachine stateMachine;
    private final OrderProperties orderProperties;
    private final CreditProperties creditProperties;

    /** 管理员指派：待处理 → 已分配。 */
    @Transactional
    public OrderDtos.FireResult assign(Long orderId, Long repairerId, String remark, LoginUser me) {
        SysUser repairer = requireRepairer(repairerId);
        OrderStatus to = stateMachine.fire(orderId, OrderAction.ASSIGN, me,
                remark, Map.of("repairerId", repairer.getId()));
        log.info("指派工单 orderId={} -> repairer={} by={}", orderId, repairerId, me.getId());
        return result(orderId, to);
    }

    /** 转派：已分配 → 已分配，换一个维修人。 */
    @Transactional
    public OrderDtos.FireResult transfer(Long orderId, Long repairerId, String remark, LoginUser me) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工单不存在");
        }
        if (repairerId != null && repairerId.equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "该工单已由此人负责，无需转派");
        }
        SysUser repairer = requireRepairer(repairerId);
        OrderStatus to = stateMachine.fire(orderId, OrderAction.TRANSFER, me,
                remark, Map.of("repairerId", repairer.getId()));
        return result(orderId, to);
    }

    /**
     * 抢单：待处理 → 已接单（跳过「已分配」）。
     *
     * <p>前置：维修工角色、账号启用、信誉分不低于阈值。
     * 并发时只有一个人能成功，其余收到 409「该工单已被他人接单」。</p>
     */
    @Transactional
    public OrderDtos.FireResult grab(Long orderId, LoginUser me) {
        SysUser repairer = userMapper.selectById(me.getId());
        if (repairer == null || repairer.getStatus() == null || repairer.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        int score = repairer.getCreditScore() == null ? creditProperties.getInitScore() : repairer.getCreditScore();
        if (score < creditProperties.getLimitThreshold()) {
            throw new BizException(ResultCode.CREDIT_LOW_FOR_GRAB,
                    "信誉分 " + score + " 低于 " + creditProperties.getLimitThreshold() + "，暂时无法抢单");
        }
        OrderStatus to;
        try {
            to = stateMachine.fire(orderId, OrderAction.GRAB, me, "维修人员抢单", Map.of());
        } catch (BizException e) {
            if (e.getCode() == ResultCode.STATUS_CHANGED.getCode()) {
                throw BizException.conflict(ResultCode.ORDER_GRABBED, "该工单已被他人接单");
            }
            // 慢一步到达：状态已经被别人从「待处理」推走了
            if (e.getCode() == ResultCode.STATUS_NOT_ALLOWED.getCode()) {
                RepairOrder current = orderMapper.selectById(orderId);
                if (current != null && (current.isStatus(OrderStatus.ACCEPTED)
                        || current.isStatus(OrderStatus.ASSIGNED))) {
                    throw BizException.conflict(ResultCode.ORDER_GRABBED,
                            "该工单已被" + assigneeNameOf(current) + "接单");
                }
            }
            throw e;
        }
        log.info("抢单成功 orderId={} repairer={}", orderId, me.getId());
        return result(orderId, to);
    }

    /** 确认接单：已分配 → 已接单。 */
    @Transactional
    public OrderDtos.FireResult accept(Long orderId, LoginUser me) {
        OrderStatus to = stateMachine.fire(orderId, OrderAction.ACCEPT, me, "确认接单", Map.of());
        return result(orderId, to);
    }

    /** 退单：已分配 → 待处理，回到抢单池；退单人扣信誉分（基线 A11）。 */
    @Transactional
    public OrderDtos.FireResult returnOrder(Long orderId, String reason, LoginUser me) {
        OrderStatus to = stateMachine.fire(orderId, OrderAction.RETURN, me,
                reason == null || reason.isBlank() ? "维修人员退单" : reason, Map.of());
        userService.adjustCredit(me.getId(), -creditProperties.getDeductScore(),
                "退单（工单 " + orderId + "）", orderId, null);
        log.info("退单 orderId={} repairer={} 扣信誉分={}", orderId, me.getId(), creditProperties.getDeductScore());
        return result(orderId, to);
    }

    /**
     * 超时自动派单：扫描超过 N 小时（默认 24）仍无人接单的工单，
     * 派给「同部门、启用、信誉达标、在办最少」的维修工。
     *
     * <p>逐单独立处理：某单派不出去不影响其它单；无候选时保留在抢单池等人工介入。</p>
     *
     * @return 本次成功派出的单数
     */
    public int autoAssign() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(orderProperties.getAutoAssignHours());
        List<RepairOrder> candidates = orderMapper.selectAutoAssignCandidates(deadline);
        log.info("自动派单扫描：截止时间 {}，待派工单 {} 张", deadline, candidates.size());
        if (candidates.isEmpty()) {
            return 0;
        }

        List<SysUser> repairers = activeRepairers();
        log.info("自动派单候选维修工 {} 人", repairers.size());
        if (repairers.isEmpty()) {
            log.warn("自动派单跳过：没有启用中的维修人员，待派工单 {} 张", candidates.size());
            return 0;
        }
        Map<Long, Integer> wip = wipCount();
        Map<Long, String> deptNames = deptMap();

        int assigned = 0;
        for (RepairOrder order : candidates) {
            SysUser pick = pickRepairer(order, repairers, wip);
            if (pick == null) {
                log.warn("自动派单无候选维修工 orderId={} dept={}", order.getId(), order.getDeptId());
                continue;
            }
            try {
                stateMachine.fire(order.getId(), OrderAction.ASSIGN, null,
                        "超过 " + orderProperties.getAutoAssignHours() + " 小时无人接单，系统自动派单给 "
                                + pick.getRealName() + "（" + deptNames.getOrDefault(pick.getDeptId(), "未分组") + "）",
                        Map.of("repairerId", pick.getId()));
                orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                        .eq(RepairOrder::getId, order.getId())
                        .set(RepairOrder::getAutoAssigned, 1));
                wip.merge(pick.getId(), 1, Integer::sum);
                assigned++;
                log.info("自动派单 orderId={} -> repairer={}", order.getId(), pick.getId());
            } catch (BizException e) {
                // 期间被抢单 / 被人指派 / 状态已变，跳过即可
                log.info("自动派单跳过 orderId={} reason={}", order.getId(), e.getMessage());
            }
        }
        return assigned;
    }

    /** 可用维修工列表（指派下拉用，在办少的排前面）。 */
    public List<OrderDtos.RepairerVO> listRepairers() {
        Map<Long, Integer> wip = wipCount();
        Map<Long, String> deptNames = deptMap();
        return activeRepairers().stream()
                .sorted(Comparator.comparingInt((SysUser u) -> wip.getOrDefault(u.getId(), 0))
                        .thenComparing(SysUser::getRealName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(u -> new OrderDtos.RepairerVO(u.getId(), u.getRealName(), u.getStaffNo(), u.getPhone(),
                        u.getDeptId(), deptNames.get(u.getDeptId()), wip.getOrDefault(u.getId(), 0),
                        u.getCreditScore()))
                .toList();
    }

    /**
     * 维修工日历：未来 N 天已确认上门时段（指派/转派时看忙闲）。
     * 允许本人或管理端（order:assign）查看。
     */
    public OrderDtos.CalendarVO calendar(Long repairerId, int days, LoginUser me) {
        boolean manager = me.hasRole(RoleCode.MANAGER) || me.hasRole(RoleCode.ADMIN);
        if (!manager && !me.getId().equals(repairerId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能查看自己的日历");
        }
        int window = Math.min(Math.max(days, 1), 31);
        LocalDate today = LocalDate.now();
        LocalDate end = today.plusDays(window - 1L);

        List<RepairSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<RepairSchedule>()
                        .eq(RepairSchedule::getRepairerId, repairerId)
                        .eq(RepairSchedule::getStatus, "CONFIRMED")
                        .ge(RepairSchedule::getPlanDate, today)
                        .le(RepairSchedule::getPlanDate, end)
                        .orderByAsc(RepairSchedule::getPlanDate)
                        .orderByAsc(RepairSchedule::getSlotStart));

        // 补工单标题（一次批量）
        Set<Long> orderIds = schedules.stream().map(RepairSchedule::getOrderId).collect(Collectors.toSet());
        Map<Long, String> titles = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderMapper.selectBatchIds(orderIds)
                    .forEach(o -> titles.put(o.getId(), o.getTitle()));
        }

        Map<LocalDate, List<OrderDtos.CalendarSlot>> byDay = new LinkedHashMap<>();
        for (RepairSchedule s : schedules) {
            byDay.computeIfAbsent(s.getPlanDate(), k -> new ArrayList<>())
                    .add(new OrderDtos.CalendarSlot(s.getOrderId(), titles.get(s.getOrderId()),
                            s.getSlotStart() == null ? null : s.getSlotStart().toString(),
                            s.getSlotEnd() == null ? null : s.getSlotEnd().toString()));
        }
        List<OrderDtos.CalendarDay> items = byDay.entrySet().stream()
                .map(e -> new OrderDtos.CalendarDay(e.getKey().toString(), e.getValue().size(), e.getValue()))
                .toList();

        int wip = wipCount().getOrDefault(repairerId, 0);
        return new OrderDtos.CalendarVO(repairerId, window, today.toString(), end.toString(),
                wip, schedules.size(), items);
    }

    // ------------------------------------------------------------------ 选人

    /**
     * 选人规则：同部门优先 → 信誉达标 → 在办最少 → 姓名序（稳定）。
     * 部门内找不到时退化为全员范围，保证工单不会因为部门配置缺失而卡死。
     */
    private SysUser pickRepairer(RepairOrder order, List<SysUser> repairers, Map<Long, Integer> wip) {
        Set<Long> deptScope = deptService.getDescendantIds(order.getDeptId());
        List<SysUser> eligible = repairers.stream()
                .filter(u -> u.getCreditScore() == null || u.getCreditScore() >= creditProperties.getLimitThreshold())
                .toList();

        List<SysUser> sameDept = eligible.stream()
                .filter(u -> u.getDeptId() != null && deptScope.contains(u.getDeptId()))
                .toList();
        List<SysUser> pool = sameDept.isEmpty() ? eligible : sameDept;
        if (pool.isEmpty()) {
            return null;
        }
        return pool.stream()
                .min(Comparator.comparingInt((SysUser u) -> wip.getOrDefault(u.getId(), 0))
                        .thenComparing(SysUser::getRealName, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private List<SysUser> activeRepairers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .inSql(SysUser::getId,
                        "SELECT user_id FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                                + "WHERE r.code = '" + RoleCode.REPAIRER + "'")
                .orderByAsc(SysUser::getId));
    }

    private Map<Long, Integer> wipCount() {
        Map<Long, Integer> map = new HashMap<>();
        orderMapper.countWipByAssignee().forEach(row -> {
            Object id = row.get("assigneeId");
            Object cnt = row.get("cnt");
            if (id instanceof Number n && cnt instanceof Number c) {
                map.put(n.longValue(), c.intValue());
            }
        });
        return map;
    }

    private Map<Long, String> deptMap() {
        return deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
    }

    private SysUser requireRepairer(Long repairerId) {
        if (repairerId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择维修人员");
        }
        SysUser user = userMapper.selectById(repairerId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "维修人员不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "该维修人员账号已停用");
        }
        Set<String> roles = new HashSet<>(userMapper.selectRoleCodes(repairerId));
        if (!roles.contains(RoleCode.REPAIRER)) {
            throw new BizException(ResultCode.PARAM_INVALID, "该用户不是维修人员");
        }
        return user;
    }

    private OrderDtos.FireResult result(Long orderId, OrderStatus status) {
        return new OrderDtos.FireResult(orderId, status.name(), status.getLabel());
    }

    private String assigneeNameOf(RepairOrder order) {
        if (order.getAssigneeId() == null) {
            return "他人";
        }
        SysUser user = userMapper.selectById(order.getAssigneeId());
        return user == null ? "他人" : user.getRealName();
    }
}
