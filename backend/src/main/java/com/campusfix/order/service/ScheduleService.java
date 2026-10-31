package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.ScheduleStatus;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.dto.ScheduleDtos;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairSchedule;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.mapper.RepairScheduleMapper;
import com.campusfix.order.statemachine.OrderStateMachine;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 预约服务：维修工给时段 → 学生选择 → 维修工确认 → 工单转「处理中」。
 *
 * <p>基线 A14 的发起方是<b>学生</b>：维修工先把「可能上门的时间窗口」列出来，
 * 学生从里面挑一个，维修工再确认。这与「维修工直接定死上门时间」不同。</p>
 *
 * <p>A15 明确时段长度不定，所以时段只是意向窗口：{@code OFFERED} 允许多单重叠，
 * 只有 {@code CONFIRMED} 才做同维修工的重叠校验（见 {@link #checkConflict}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_SLOTS = 3;
    /** 改约产生的时段在备注里带这个前缀，前端据此显示「改约」标记 */
    private static final String RESCHEDULE_TAG = "[改约]";

    private final RepairScheduleMapper scheduleMapper;
    private final RepairOrderMapper orderMapper;
    private final SysUserMapper userMapper;
    private final OrderStateMachine stateMachine;
    private final OrderQueryService queryService;

    // ------------------------------------------------------------------ 查询

    public List<ScheduleDtos.ScheduleVO> list(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!queryService.canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单的预约");
        }
        return listOf(orderId, order, me);
    }

    /** 按时间段排序，并算出「当前登录者能做什么」。 */
    private List<ScheduleDtos.ScheduleVO> listOf(Long orderId, RepairOrder order, LoginUser me) {
        List<RepairSchedule> all = scheduleMapper.selectList(new LambdaQueryWrapper<RepairSchedule>()
                .eq(RepairSchedule::getOrderId, orderId)
                .orderByDesc(RepairSchedule::getPlanDate)
                .orderByDesc(RepairSchedule::getSlotStart));

        Map<Long, String> names = userNameMap(all.stream().map(RepairSchedule::getRepairerId).toList());
        boolean isReporter = me.getId().equals(order.getReporterId());

        return all.stream()
                .sorted(Comparator.comparing(RepairSchedule::getPlanDate)
                        .thenComparing(RepairSchedule::getSlotStart))
                .map(s -> {
                    boolean offered = ScheduleStatus.OFFERED.name().equals(s.getStatus());
                    boolean reschedule = isReschedule(s);
                    // 报修人：普通时段是「选中」，改约提议是「接受」；两者互斥
                    boolean acceptable = isReporter && offered && reschedule;
                    boolean selectable = isReporter && offered && !reschedule
                            && (order.isStatus(OrderStatus.ACCEPTED) || order.isStatus(OrderStatus.PROCESSING));
                    return new ScheduleDtos.ScheduleVO(
                            s.getId(),
                            s.getOrderId(),
                            s.getRepairerId(),
                            names.get(s.getRepairerId()),
                            s.getPlanDate() == null ? null : s.getPlanDate().toString(),
                            s.getSlotStart() == null ? null : s.getSlotStart().format(TIME),
                            s.getSlotEnd() == null ? null : s.getSlotEnd().format(TIME),
                            s.getStatus(),
                            labelOf(s.getStatus()),
                            s.getProposedBy(),
                            s.getRemark(),
                            reschedule,
                            selectable,
                            acceptable);
                })
                .toList();
    }

    // ------------------------------------------------------------------ 给时段

    /**
     * 维修工给出 1–3 个可上门时段。
     *
     * <p>重复调用会作废上一批还没被选走的时段，避免页面上堆一串过期提议。</p>
     */
    @Transactional
    public List<ScheduleDtos.ScheduleVO> offer(Long orderId, ScheduleDtos.OfferRequest req, LoginUser me) {
        RepairOrder order = requireAssigneeOrder(orderId, me, OrderStatus.ACCEPTED);
        if (req.slots().size() > MAX_SLOTS) {
            throw new BizException(ResultCode.PARAM_INVALID, "一次最多提供 " + MAX_SLOTS + " 个时段");
        }
        req.slots().forEach(ScheduleDtos.SlotRequest::validate);

        // 作废上一批仍在等待选择的时段
        cancelByStatus(orderId, ScheduleStatus.OFFERED, "维修人员重新提供时段");

        for (ScheduleDtos.SlotRequest slot : req.slots()) {
            insertSlot(order, me.getId(), slot, false);
        }
        log.info("维修工给出时段 orderId={} repairer={} 数量={}", orderId, me.getId(), req.slots().size());
        return listOf(orderId, order, me);
    }

    private void insertSlot(RepairOrder order, Long repairerId, ScheduleDtos.SlotRequest slot, boolean reschedule) {
        RepairSchedule s = new RepairSchedule();
        s.setOrderId(order.getId());
        s.setRepairerId(repairerId);
        s.setPlanDate(slot.planDate());
        s.setSlotStart(slot.slotStart());
        s.setSlotEnd(slot.slotEnd());
        s.setStatus(ScheduleStatus.OFFERED.name());
        s.setProposedBy("REPAIRER");
        s.setRemark(reschedule ? RESCHEDULE_TAG + suffix(slot.remark()) : suffix(slot.remark()));
        scheduleMapper.insert(s);
    }

    private static String suffix(String remark) {
        return remark == null || remark.isBlank() ? null : remark.trim();
    }

    // ------------------------------------------------------------------ 学生选择

    @Transactional
    public List<ScheduleDtos.ScheduleVO> select(Long scheduleId, LoginUser me) {
        RepairSchedule target = requireSlotById(scheduleId);
        Long orderId = target.getOrderId();
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getReporterId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有报修人可以选择上门时间");
        }
        if (!order.isStatus(OrderStatus.ACCEPTED) && !order.isStatus(OrderStatus.PROCESSING)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "当前状态「" + order.statusEnum().getLabel() + "」不能选择上门时间");
        }
        if (!ScheduleStatus.OFFERED.name().equals(target.getStatus())) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "该时段已被处理，请刷新后重试");
        }
        if (isReschedule(target)) {
            throw new BizException(ResultCode.PARAM_INVALID, "这是维修人员的改约提议，请使用「接受改约」");
        }

        // 其余同批时段一并作废，避免学生反复改主意导致多个 SELECTED
        cancelOthers(orderId, target.getId(), ScheduleStatus.OFFERED);
        updateStatus(target.getId(), ScheduleStatus.SELECTED);
        log.info("学生选定时段 orderId={} scheduleId={}", orderId, scheduleId);
        return listOf(orderId, order, me);
    }

    // ------------------------------------------------------------------ 维修工确认

    /**
     * 确认时段：写回工单 {@code plan_time} 并把状态推到「处理中」。
     * 确认前做一次同维修工已确认时段的重叠校验（A15 的唯一排他口径）。
     */
    @Transactional
    public List<ScheduleDtos.ScheduleVO> confirm(Long scheduleId, LoginUser me) {
        RepairSchedule target = requireSlotById(scheduleId);
        Long orderId = target.getOrderId();
        RepairOrder order = requireAssigneeOrder(orderId, me, OrderStatus.ACCEPTED);
        if (!ScheduleStatus.SELECTED.name().equals(target.getStatus())) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "该时段还不处于「学生已选」状态");
        }
        checkConflict(target.getRepairerId(), target.getPlanDate(),
                target.getSlotStart(), target.getSlotEnd(), target.getId());

        updateStatus(target.getId(), ScheduleStatus.CONFIRMED);
        stateMachine.fire(orderId, OrderAction.SCHEDULE_CONFIRM, me,
                "约定上门时间 " + target.getPlanDate() + " " + target.getSlotStart().format(TIME)
                        + "-" + target.getSlotEnd().format(TIME),
                Map.of("planTime", target.planTime()));
        log.info("确认预约 orderId={} scheduleId={} planTime={}", orderId, scheduleId, target.planTime());
        return listOf(orderId, order, me);
    }

    // ------------------------------------------------------------------ 改约

    /**
     * 维修工改约：新增一个标了「改约」的提议时段，原时段先保留，等学生响应。
     */
    @Transactional
    public List<ScheduleDtos.ScheduleVO> reschedule(Long orderId, ScheduleDtos.SlotRequest slot, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有当前维修人可以发起改约");
        }
        if (!order.isStatus(OrderStatus.ACCEPTED) && !order.isStatus(OrderStatus.PROCESSING)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "当前状态「" + order.statusEnum().getLabel() + "」不能改约");
        }
        slot.validate();
        insertSlot(order, me.getId(), slot, true);
        log.info("维修工发起改约 orderId={} 新时段 {} {}", orderId, slot.planDate(), slot.slotStart());
        return listOf(orderId, order, me);
    }

    /** 学生接受改约：新时段转 CONFIRMED，同单其余时段作废，工单状态不变。 */
    @Transactional
    public List<ScheduleDtos.ScheduleVO> acceptReschedule(Long scheduleId, LoginUser me) {
        RepairSchedule target = requireSlotById(scheduleId);
        Long orderId = target.getOrderId();
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getReporterId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有报修人可以接受改约");
        }
        if (!isReschedule(target) || !ScheduleStatus.OFFERED.name().equals(target.getStatus())) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "该时段不是待响应的改约提议");
        }
        checkConflict(target.getRepairerId(), target.getPlanDate(),
                target.getSlotStart(), target.getSlotEnd(), target.getId());

        cancelOthers(orderId, target.getId(), null);
        updateStatus(target.getId(), ScheduleStatus.CONFIRMED);

        // 工单可能还停在「已接单」（未确认过首次预约），此时同步推进到处理中
        if (order.isStatus(OrderStatus.ACCEPTED)) {
            stateMachine.fire(orderId, OrderAction.SCHEDULE_CONFIRM, me,
                    "接受改约 " + target.getPlanDate() + " " + target.getSlotStart().format(TIME),
                    Map.of("planTime", target.planTime()));
        } else {
            orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                    .eq(RepairOrder::getId, orderId)
                    .set(RepairOrder::getPlanTime, target.planTime()));
        }
        log.info("学生接受改约 orderId={} scheduleId={}", orderId, scheduleId);
        return listOf(orderId, order, me);
    }

    /**
     * 学生拒绝改约：作废全部时段，工单回到抢单池重新分配（A14 / 基线 §7.3）。
     */
    @Transactional
    public List<ScheduleDtos.ScheduleVO> rejectReschedule(Long scheduleId, String reason, LoginUser me) {
        RepairSchedule target = requireSlotById(scheduleId);
        Long orderId = target.getOrderId();
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getReporterId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有报修人可以拒绝改约");
        }
        cancelByStatus(orderId, null, "报修人拒绝改约，工单重新分配");
        stateMachine.fire(orderId, OrderAction.RESCHEDULE_REJECT, me,
                reason == null || reason.isBlank() ? "报修人拒绝改约，工单重新分配" : reason,
                Map.of());
        log.info("学生拒绝改约 orderId={}，工单回待处理", orderId);
        return List.of();
    }

    // ------------------------------------------------------------------ 内部

    private RepairOrder requireAssigneeOrder(Long orderId, LoginUser me, OrderStatus expect) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有当前维修人可以操作该工单的预约");
        }
        if (!order.isStatus(expect)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "当前状态「" + order.statusEnum().getLabel() + "」不能" + expect.getLabel() + "相关操作");
        }
        return order;
    }

    private RepairSchedule requireSlotById(Long scheduleId) {
        RepairSchedule s = scheduleMapper.selectById(scheduleId);
        if (s == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预约时段不存在");
        }
        return s;
    }

    private void updateStatus(Long scheduleId, ScheduleStatus status) {
        RepairSchedule update = new RepairSchedule();
        update.setId(scheduleId);
        update.setStatus(status.name());
        scheduleMapper.updateById(update);
    }

    /** 作废同单内除 exceptId 外、指定状态的时段；status 为 null 表示全部状态。 */
    private void cancelOthers(Long orderId, Long exceptId, ScheduleStatus status) {
        LambdaUpdateWrapper<RepairSchedule> w = new LambdaUpdateWrapper<RepairSchedule>()
                .eq(RepairSchedule::getOrderId, orderId)
                .ne(RepairSchedule::getId, exceptId)
                .ne(RepairSchedule::getStatus, ScheduleStatus.CANCELLED.name())
                .set(RepairSchedule::getStatus, ScheduleStatus.CANCELLED.name());
        if (status != null) {
            w.eq(RepairSchedule::getStatus, status.name());
        }
        scheduleMapper.update(null, w);
    }

    private void cancelByStatus(Long orderId, ScheduleStatus status, String reason) {
        LambdaUpdateWrapper<RepairSchedule> w = new LambdaUpdateWrapper<RepairSchedule>()
                .eq(RepairSchedule::getOrderId, orderId)
                .ne(RepairSchedule::getStatus, ScheduleStatus.CANCELLED.name())
                .set(RepairSchedule::getStatus, ScheduleStatus.CANCELLED.name())
                .set(RepairSchedule::getRemark, reason);
        if (status != null) {
            w.eq(RepairSchedule::getStatus, status.name());
        }
        scheduleMapper.update(null, w);
    }

    /**
     * 冲突检测：同一维修工在同一天已确认的时段不允许重叠。
     *
     * <p>只查 {@code CONFIRMED}。{@code OFFERED} / {@code SELECTED} 是意向，
     * 允许重叠 —— 这是 A15「时长不确定」的现实口径。</p>
     */
    private void checkConflict(Long repairerId, LocalDate date, LocalTime start, LocalTime end, Long excludeId) {
        Long count = scheduleMapper.selectCount(new LambdaQueryWrapper<RepairSchedule>()
                .eq(RepairSchedule::getRepairerId, repairerId)
                .eq(RepairSchedule::getPlanDate, date)
                .eq(RepairSchedule::getStatus, ScheduleStatus.CONFIRMED.name())
                .lt(RepairSchedule::getSlotStart, end)
                .gt(RepairSchedule::getSlotEnd, start)
                .ne(excludeId != null, RepairSchedule::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.SCHEDULE_CONFLICT,
                    "该时段与已确认的上门安排重叠，请换一个时间");
        }
    }

    private boolean isReschedule(RepairSchedule s) {
        return s.getRemark() != null && s.getRemark().startsWith(RESCHEDULE_TAG);
    }

    private static String labelOf(String status) {
        try {
            return ScheduleStatus.valueOf(status).getLabel();
        } catch (Exception e) {
            return status;
        }
    }

    private Map<Long, String> userNameMap(List<Long> ids) {
        List<Long> clean = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (clean.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(clean).stream()
                .collect(java.util.stream.Collectors.toMap(SysUser::getId, SysUser::getRealName,
                        (a, b) -> a));
    }
}
