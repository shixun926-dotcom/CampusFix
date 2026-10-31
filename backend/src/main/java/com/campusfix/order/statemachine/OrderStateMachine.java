package com.campusfix.order.statemachine;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.asset.service.FacilityService;
import com.campusfix.message.service.NotificationService;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairOrderLog;
import com.campusfix.order.mapper.RepairOrderLogMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.campusfix.common.enums.OrderAction.ACCEPT;
import static com.campusfix.common.enums.OrderAction.ASSIGN;
import static com.campusfix.common.enums.OrderAction.AUTO_CONFIRM;
import static com.campusfix.common.enums.OrderAction.CANCEL;
import static com.campusfix.common.enums.OrderAction.CONFIRM;
import static com.campusfix.common.enums.OrderAction.GRAB;
import static com.campusfix.common.enums.OrderAction.RESCHEDULE_REJECT;
import static com.campusfix.common.enums.OrderAction.RESUME;
import static com.campusfix.common.enums.OrderAction.RETURN;
import static com.campusfix.common.enums.OrderAction.REVIEW_PASS;
import static com.campusfix.common.enums.OrderAction.REVIEW_REJECT;
import static com.campusfix.common.enums.OrderAction.REWORK;
import static com.campusfix.common.enums.OrderAction.SCHEDULE_CONFIRM;
import static com.campusfix.common.enums.OrderAction.SUBMIT_RESULT;
import static com.campusfix.common.enums.OrderAction.SUSPEND;
import static com.campusfix.common.enums.OrderAction.TRANSFER;
import static com.campusfix.common.enums.OrderAction.UNREPAIRABLE;
import static com.campusfix.common.enums.OrderStatus.ACCEPTED;
import static com.campusfix.common.enums.OrderStatus.ASSIGNED;
import static com.campusfix.common.enums.OrderStatus.CANCELLED;
import static com.campusfix.common.enums.OrderStatus.COMPLETED;
import static com.campusfix.common.enums.OrderStatus.PENDING;
import static com.campusfix.common.enums.OrderStatus.PENDING_CONFIRM;
import static com.campusfix.common.enums.OrderStatus.PENDING_REVIEW;
import static com.campusfix.common.enums.OrderStatus.PROCESSING;
import static com.campusfix.common.enums.OrderStatus.REJECTED;
import static com.campusfix.common.enums.OrderStatus.SUSPENDED;

/**
 * 工单状态机：状态流转的唯一入口。
 *
 * <p>约束（见《系统设计说明书》§3.3 与 §9 ADR-06）：</p>
 * <ol>
 *   <li>业务代码<b>禁止</b>直接 {@code order.setStatus(...)}，只能调用 {@link #fire}；</li>
 *   <li>转换表集中在本类，新增状态只改这里，不改散落业务代码；</li>
 *   <li>每次流转必须写 {@code repair_order_log}，它是时间轴与统计的唯一数据源；</li>
 *   <li>并发安全靠 {@code version} 乐观锁：条件更新影响行数为 0 即视为被他人抢先，返回 409。</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    /** 按动作索引的转换表；同一动作可有多个来源状态 */
    private static final Map<OrderAction, List<Transition>> TABLE = buildTable();

    private final RepairOrderMapper orderMapper;
    private final RepairOrderLogMapper logMapper;
    private final NotificationService notificationService;
    private final FacilityService facilityService;

    /**
     * 执行一次状态流转。
     *
     * @param operator 操作人，null 表示系统触发（定时任务）
     * @param param    业务参数，如指派的 {@code repairerId}
     * @return 流转后的状态
     */
    @Transactional
    public OrderStatus fire(Long orderId, OrderAction action, LoginUser operator,
                            String remark, Map<String, Object> param) {
        RepairOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ResultCode.NOT_FOUND, "工单不存在");
        }
        OrderStatus from = OrderStatus.of(order.getStatus());
        Transition transition = find(action, from);
        if (transition == null) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "当前状态「" + from.getLabel() + "」不允许执行「" + action.getLabel() + "」");
        }

        Set<String> opRoles = operator == null ? Set.of(RoleCode.SYSTEM) : operator.getRoles();
        if (!transition.roleAllowed(opRoles)) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "「" + from.getLabel() + "」状态下只有 " + roleText(transition.roles()) + " 可以执行「"
                            + action.getLabel() + "」");
        }

        OrderContext ctx = new OrderContext(order, operator, remark, param);
        if (transition.guard() != null && !transition.guard().test(ctx)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, transition.guardMessage());
        }

        int oldVersion = order.getVersion() == null ? 0 : order.getVersion();
        LambdaUpdateWrapper<RepairOrder> update = new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .eq(RepairOrder::getVersion, oldVersion)
                .set(RepairOrder::getStatus, transition.to().name())
                .set(RepairOrder::getVersion, oldVersion + 1)
                .set(RepairOrder::getUpdatedAt, LocalDateTime.now());
        if (transition.effect() != null) {
            transition.effect().apply(ctx, update);
        }

        int rows = orderMapper.update(null, update);
        if (rows == 0) {
            log.warn("工单流转并发冲突 orderId={} action={} version={}", orderId, action, oldVersion);
            throw BizException.conflict(ResultCode.STATUS_CHANGED,
                    "工单已被他人处理，请刷新后重试");
        }

        writeLog(orderId, from, transition.to(), action, operator,
                transition.primaryRole(opRoles), remark);
        // 站内通知：按「基线 §11.4 触发点」分发给报修人/维修人（排除操作人本人），
        // 传流转前的 order 快照，REWORK/RESCHEDULE_REJECT 才能通知到原维修人
        notificationService.onTransition(order, action, transition.to(), operator, remark, param);
        // 设施状态联动（基线 §9.2）：处理中→REPAIRING，验收完成→NORMAL
        facilityService.onTransition(order, action);
        log.info("工单流转 orderId={} {} -> {} action={} operator={}",
                orderId, from, transition.to(), action, operator == null ? "SYSTEM" : operator.getId());
        return transition.to();
    }

    /** 建单时写第一条日志（from 为 null）。 */
    public void logCreation(RepairOrder order, LoginUser operator) {
        OrderStatus to = OrderStatus.of(order.getStatus());
        writeLog(order.getId(), null, to, null, operator,
                operator == null ? RoleCode.SYSTEM : primaryRoleOf(operator), "提交报修");
    }

    public Transition find(OrderAction action, OrderStatus from) {
        return TABLE.getOrDefault(action, List.of()).stream()
                .filter(t -> t.appliesTo(from))
                .findFirst()
                .orElse(null);
    }

    /** 当前状态 + 动作是否可执行（供前端按钮可用性判断，不抛异常）。 */
    public boolean canFire(OrderStatus from, OrderAction action) {
        return find(action, from) != null;
    }

    private void writeLog(Long orderId, OrderStatus from, OrderStatus to, OrderAction action,
                          LoginUser operator, String role, String remark) {
        RepairOrderLog entry = new RepairOrderLog();
        entry.setOrderId(orderId);
        entry.setFromStatus(from == null ? null : from.name());
        entry.setToStatus(to.name());
        entry.setAction(action == null ? "CREATE" : action.name());
        entry.setOperatorId(operator == null ? 0L : operator.getId());
        entry.setOperatorRole(role);
        entry.setRemark(trim(remark));
        logMapper.insert(entry);
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        String v = s.trim();
        return v.isEmpty() ? null : (v.length() > 500 ? v.substring(0, 500) : v);
    }

    private static String primaryRoleOf(LoginUser operator) {
        Set<String> roles = operator.getRoles();
        return roles == null || roles.isEmpty() ? RoleCode.STUDENT : roles.iterator().next();
    }

    private static String roleText(Set<String> roles) {
        List<String> names = new ArrayList<>();
        for (String r : roles) {
            names.add(switch (r) {
                case RoleCode.STUDENT -> "学生";
                case RoleCode.REPAIRER -> "维修人员";
                case RoleCode.MANAGER -> "后勤管理员";
                case RoleCode.ADMIN -> "系统管理员";
                default -> "系统";
            });
        }
        return String.join(" / ", names);
    }

    // ------------------------------------------------------------------ 转换表

    private static Set<String> roles(String... codes) {
        return Set.of(codes);
    }

    private static Set<OrderStatus> froms(OrderStatus... statuses) {
        return EnumSet.copyOf(List.of(statuses));
    }

    private static Transition t(OrderAction action, Set<OrderStatus> froms, OrderStatus to,
                                Set<String> roles, Transition.TransitionEffect effect) {
        return new Transition(action, froms, to, roles, null, null, effect);
    }

    private static Transition t(OrderAction action, Set<OrderStatus> froms, OrderStatus to,
                                Set<String> roles, java.util.function.Predicate<OrderContext> guard,
                                String guardMessage, Transition.TransitionEffect effect) {
        return new Transition(action, froms, to, roles, guard, guardMessage, effect);
    }

    private static Map<OrderAction, List<Transition>> buildTable() {
        Map<OrderAction, List<Transition>> table = new EnumMap<>(OrderAction.class);

        // 审核环节
        put(table, t(REVIEW_PASS, froms(PENDING_REVIEW), PENDING, roles(RoleCode.MANAGER, RoleCode.ADMIN),
                (ctx, u) -> {
                    u.set(RepairOrder::getReviewBy, ctx.operatorId());
                    u.set(RepairOrder::getReviewAt, LocalDateTime.now());
                }));
        put(table, t(REVIEW_REJECT, froms(PENDING_REVIEW), REJECTED, roles(RoleCode.MANAGER, RoleCode.ADMIN),
                (ctx, u) -> {
                    u.set(RepairOrder::getReviewBy, ctx.operatorId());
                    u.set(RepairOrder::getReviewAt, LocalDateTime.now());
                    u.set(RepairOrder::getRejectReason, ctx.remark());
                }));

        // 派单环节：指派 / 转派 / 抢单 / 接单 / 退单
        // ASSIGN 额外允许 SYSTEM —— 24h 超时自动派单由定时任务以系统身份执行
        put(table, t(ASSIGN, froms(PENDING), ASSIGNED,
                roles(RoleCode.MANAGER, RoleCode.ADMIN, RoleCode.SYSTEM),
                ctx -> ctx.paramLong("repairerId") != null, "请先选择维修人员",
                (ctx, u) -> u.set(RepairOrder::getAssigneeId, ctx.paramLong("repairerId"))));
        put(table, t(TRANSFER, froms(ASSIGNED), ASSIGNED, roles(RoleCode.MANAGER, RoleCode.ADMIN),
                ctx -> ctx.paramLong("repairerId") != null, "请先选择接收转派的维修人员",
                (ctx, u) -> {
                    u.set(RepairOrder::getAssigneeId, ctx.paramLong("repairerId"));
                    u.set(RepairOrder::getAutoAssigned, 0);
                }));
        // 抢单跳过「已分配」，直接到「已接单」
        put(table, t(GRAB, froms(PENDING), ACCEPTED, roles(RoleCode.REPAIRER),
                (ctx, u) -> {
                    u.set(RepairOrder::getAssigneeId, ctx.operatorId());
                    u.set(RepairOrder::getAcceptedAt, LocalDateTime.now());
                    u.set(RepairOrder::getAutoAssigned, 0);
                }));
        put(table, t(ACCEPT, froms(ASSIGNED), ACCEPTED, roles(RoleCode.REPAIRER),
                ctx -> ctx.operatorIs(OrderContext.RepairOrderRole.ASSIGNEE), "该工单未指派给你，无法接单",
                (ctx, u) -> u.set(RepairOrder::getAcceptedAt, LocalDateTime.now())));
        put(table, t(RETURN, froms(ASSIGNED), PENDING, roles(RoleCode.REPAIRER),
                ctx -> ctx.operatorIs(OrderContext.RepairOrderRole.ASSIGNEE), "该工单未指派给你，无法退单",
                (ctx, u) -> {
                    u.set(RepairOrder::getAssigneeId, null);
                    u.set(RepairOrder::getAcceptedAt, null);
                    u.set(RepairOrder::getAutoAssigned, 0);
                }));

        // 预约环节。改约可能发生在「已接单」（还没确认上门）与「处理中」（已上门但仍需换时间）两处，
        // 学生拒绝后一律回到抢单池重新分配（基线 A14）。
        put(table, t(SCHEDULE_CONFIRM, froms(ACCEPTED), PROCESSING, roles(RoleCode.REPAIRER),
                (ctx, u) -> u.set(RepairOrder::getPlanTime,
                        ctx.param("planTime") instanceof LocalDateTime p ? p : LocalDateTime.now())));
        put(table, t(RESCHEDULE_REJECT, froms(ACCEPTED, PROCESSING), PENDING, roles(),
                ctx -> ctx.operatorIs(OrderContext.RepairOrderRole.REPORTER), "只有报修人能否决预约",
                (ctx, u) -> {
                    u.set(RepairOrder::getAssigneeId, null);
                    u.set(RepairOrder::getPlanTime, null);
                    u.set(RepairOrder::getAcceptedAt, null);
                }));

        // 维修过程
        put(table, t(SUSPEND, froms(PROCESSING), SUSPENDED, roles(RoleCode.REPAIRER),
                (ctx, u) -> u.set(RepairOrder::getSuspendStartAt, LocalDateTime.now())));
        put(table, t(RESUME, froms(SUSPENDED), PROCESSING, roles(RoleCode.REPAIRER),
                (ctx, u) -> u.setSql("suspend_minutes = suspend_minutes + "
                        + "TIMESTAMPDIFF(MINUTE, COALESCE(suspend_start_at, NOW()), NOW())")
                        .set(RepairOrder::getSuspendStartAt, null)));
        put(table, t(SUBMIT_RESULT, froms(PROCESSING, SUSPENDED), PENDING_CONFIRM,
                roles(RoleCode.REPAIRER),
                (ctx, u) -> {
                    u.set(RepairOrder::getFinishedAt, LocalDateTime.now());
                    u.set(RepairOrder::getSuspendStartAt, null);
                    // 自动验收截止时间：调用方传准确天数，未传时按 3 天兜底
                    u.set(RepairOrder::getConfirmDeadline,
                            ctx.param("confirmDeadline") instanceof LocalDateTime d
                                    ? d : LocalDateTime.now().plusDays(3));
                }));
        put(table, t(UNREPAIRABLE, froms(PROCESSING, SUSPENDED), REJECTED, roles(RoleCode.REPAIRER),
                (ctx, u) -> u.set(RepairOrder::getRejectReason,
                        ctx.remark().isEmpty() ? "维修人员判定无法维修" : ctx.remark())));

        // 验收环节
        // 报修人可能是学生、维修工或管理员（基线允许任意角色报修），
        // 因此这里不看角色，只看「是不是本单报修人」——由 guard 判定。
        put(table, t(CONFIRM, froms(PENDING_CONFIRM), COMPLETED, roles(),
                ctx -> ctx.operatorIs(OrderContext.RepairOrderRole.REPORTER), "只有报修人本人可以确认完成",
                (ctx, u) -> {
                    u.set(RepairOrder::getConfirmedAt, LocalDateTime.now());
                    u.set(RepairOrder::getConfirmDeadline, null);
                    u.set(RepairOrder::getAutoConfirmed, 0);
                }));
        put(table, t(AUTO_CONFIRM, froms(PENDING_CONFIRM), COMPLETED, roles(RoleCode.SYSTEM),
                (ctx, u) -> {
                    u.set(RepairOrder::getConfirmedAt, LocalDateTime.now());
                    u.set(RepairOrder::getAutoConfirmed, 1);
                    u.set(RepairOrder::getConfirmDeadline, null);
                }));
        // 返修（方案 B）：回到抢单池，清空维修人与上门时间
        put(table, t(REWORK, froms(PENDING_CONFIRM), PENDING, roles(),
                ctx -> ctx.operatorIs(OrderContext.RepairOrderRole.REPORTER), "只有报修人本人可以申请返修",
                (ctx, u) -> {
                    u.set(RepairOrder::getAssigneeId, null);
                    u.set(RepairOrder::getPlanTime, null);
                    u.set(RepairOrder::getReworkFlag, 1);
                    u.set(RepairOrder::getConfirmDeadline, null);
                    u.setSql("rework_count = rework_count + 1");
                }));

        // 取消：所有未完结状态，仅管理员
        put(table, t(CANCEL, froms(OrderStatus.activeStatuses().toArray(new OrderStatus[0])), CANCELLED,
                roles(RoleCode.MANAGER, RoleCode.ADMIN),
                (ctx, u) -> u.set(RepairOrder::getCancelledAt, LocalDateTime.now())));

        return table;
    }

    private static void put(Map<OrderAction, List<Transition>> table, Transition transition) {
        table.computeIfAbsent(transition.action(), k -> new ArrayList<>()).add(transition);
    }

    /** 供测试与文档生成：全部合法边。 */
    public static List<Transition> allTransitions() {
        List<Transition> all = new ArrayList<>();
        TABLE.values().forEach(all::addAll);
        return all;
    }

    /** 调试用：按动作输出「froms -> to」。 */
    public static Map<String, String> describe() {
        Map<String, String> desc = new HashMap<>();
        allTransitions().forEach(t -> desc.put(t.action().name(),
                t.froms().stream().map(Enum::name).sorted().toList() + " -> " + t.to().name()));
        return desc;
    }
}
