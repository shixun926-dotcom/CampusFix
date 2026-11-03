package com.campusfix.order.statemachine;

import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairOrderLog;
import com.campusfix.order.mapper.RepairOrderLogMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 工单状态机单元测试。
 *
 * <p>覆盖三类核心场景：</p>
 * <ol>
 *   <li>转换表完整性：17 个动作对应的合法边是否与设计说明书 §10.1 一致；</li>
 *   <li>合法性裁决：非法流转、角色不符、前置守卫不满足均被拒绝；</li>
 *   <li>并发安全：乐观锁 CAS 影响行数为 0 时返回 409。</li>
 * </ol>
 *
 * <p>状态机是系统核心且最易出错的部分（纯靠手工回归易引入「卡死工单」缺陷），
 * 因此用纯 Mockito 单测（不启动 Spring 容器）锁定其行为。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderStateMachineTest {

    @Mock
    private RepairOrderMapper orderMapper;

    @Mock
    private RepairOrderLogMapper logMapper;

    @Mock
    private com.campusfix.message.service.NotificationService notificationService;

    @Mock
    private com.campusfix.asset.service.FacilityService facilityService;

    private OrderStateMachine machine;

    private static final long ORDER_ID = 1L;
    private static final long REPORTER_ID = 100L;
    private static final long ASSIGNEE_ID = 200L;

    @BeforeEach
    void setUp() {
        machine = new OrderStateMachine(orderMapper, logMapper, notificationService, facilityService);
        lenient().when(logMapper.insert(any(RepairOrderLog.class))).thenReturn(1);
    }

    /**
     * 初始化 MyBatis-Plus 表元数据缓存。
     *
     * <p>{@code OrderStateMachine.fire()} 内部使用 {@code LambdaUpdateWrapper} 的
     * {@code set(RepairOrder::getXxx, ...)}，依赖 {@link TableInfoHelper} 已注册
     * {@code RepairOrder} 的列缓存。纯 Mockito 单测（不启动 Spring 容器）不会自动注册，
     * 故在此手动初始化，否则会抛「can not find lambda cache for this entity」。</p>
     */
    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, RepairOrder.class);
        TableInfoHelper.initTableInfo(assistant, RepairOrderLog.class);
    }

    // ------------------------------------------------------------------ 工具

    private RepairOrder order(OrderStatus status) {
        RepairOrder o = new RepairOrder();
        o.setId(ORDER_ID);
        o.setReporterId(REPORTER_ID);
        o.setStatus(status.name());
        o.setVersion(0);
        o.setAssigneeId(ASSIGNEE_ID);
        return o;
    }

    private LoginUser user(long id, String... roles) {
        LoginUser u = new LoginUser(id, "u" + id, "用户" + id);
        u.setRoles(new java.util.LinkedHashSet<>(List.of(roles)));
        return u;
    }

    private void stubSelect(RepairOrder o) {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(o);
    }

    private void stubUpdateSuccess() {
        when(orderMapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class))).thenReturn(1);
    }

    private void stubUpdateConflict() {
        when(orderMapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class))).thenReturn(0);
    }

    // ------------------------------------------------------------------ 转换表完整性

    @Test
    @DisplayName("转换表应包含全部 17 个动作的合法边")
    void tableContainsAllActions() {
        Set<OrderAction> actions = OrderStateMachine.allTransitions().stream()
                .map(Transition::action)
                .collect(java.util.stream.Collectors.toSet());

        // 17 个动作全部有定义
        assertThat(actions).containsExactlyInAnyOrder(OrderAction.values());
    }

    @Test
    @DisplayName("审核通过：待审核 -> 待处理（MANAGER/ADMIN）")
    void reviewPassTransitionsToPending() {
        stubSelect(order(OrderStatus.PENDING_REVIEW));
        stubUpdateSuccess();

        OrderStatus to = machine.fire(ORDER_ID, OrderAction.REVIEW_PASS,
                user(1L, RoleCode.MANAGER), "通过", Map.of());

        assertThat(to).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("抢单跳过已分配，直接到已接单")
    void grabGoesDirectlyToAccepted() {
        stubSelect(order(OrderStatus.PENDING));
        stubUpdateSuccess();

        OrderStatus to = machine.fire(ORDER_ID, OrderAction.GRAB,
                user(ASSIGNEE_ID, RoleCode.REPAIRER), null, Map.of());

        assertThat(to).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("系统身份可执行自动派单（24h 超时）")
    void systemCanAutoAssign() {
        RepairOrder o = order(OrderStatus.PENDING);
        o.setAssigneeId(null);
        stubSelect(o);
        stubUpdateSuccess();

        OrderStatus to = machine.fire(ORDER_ID, OrderAction.ASSIGN, null, null,
                Map.of("repairerId", ASSIGNEE_ID));

        assertThat(to).isEqualTo(OrderStatus.ASSIGNED);
    }

    @Test
    @DisplayName("系统身份可执行自动验收")
    void systemCanAutoConfirm() {
        stubSelect(order(OrderStatus.PENDING_CONFIRM));
        stubUpdateSuccess();

        OrderStatus to = machine.fire(ORDER_ID, OrderAction.AUTO_CONFIRM, null, null, Map.of());

        assertThat(to).isEqualTo(OrderStatus.COMPLETED);
    }

    // ------------------------------------------------------------------ 合法性裁决

    @Test
    @DisplayName("非法流转应抛 STATUS_NOT_ALLOWED")
    void illegalTransitionThrows() {
        // 已驳回是终态，不允许再审核通过
        stubSelect(order(OrderStatus.REJECTED));

        assertThatThrownBy(() -> machine.fire(ORDER_ID, OrderAction.REVIEW_PASS,
                user(1L, RoleCode.MANAGER), null, Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("角色不符应抛 FORBIDDEN（学生不能审核）")
    void wrongRoleThrowsForbidden() {
        stubSelect(order(OrderStatus.PENDING_REVIEW));

        assertThatThrownBy(() -> machine.fire(ORDER_ID, OrderAction.REVIEW_PASS,
                user(REPORTER_ID, RoleCode.STUDENT), null, Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("前置守卫不满足应被拒绝（指派未选维修工）")
    void guardFailsThrows() {
        stubSelect(order(OrderStatus.PENDING));

        // ASSIGN 的 guard 要求 repairerId 非空，这里不传
        assertThatThrownBy(() -> machine.fire(ORDER_ID, OrderAction.ASSIGN,
                user(1L, RoleCode.MANAGER), null, Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("非本人不能确认完成（归属守卫）")
    void nonReporterCannotConfirm() {
        // 报修人是 REPORTER_ID，操作人是别人
        stubSelect(order(OrderStatus.PENDING_CONFIRM));

        assertThatThrownBy(() -> machine.fire(ORDER_ID, OrderAction.CONFIRM,
                user(999L, RoleCode.STUDENT), null, Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("工单不存在应抛 NOT_FOUND")
    void missingOrderThrowsNotFound() {
        when(orderMapper.selectById(ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> machine.fire(ORDER_ID, OrderAction.REVIEW_PASS,
                user(1L, RoleCode.MANAGER), null, Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    // ------------------------------------------------------------------ 并发安全

    @Test
    @DisplayName("乐观锁冲突（影响行数为 0）应返回 409 状态")
    void optimisticLockConflictReturns409() {
        stubSelect(order(OrderStatus.PENDING));
        stubUpdateConflict();

        assertThatThrownBy(() -> machine.fire(ORDER_ID, OrderAction.GRAB,
                user(ASSIGNEE_ID, RoleCode.REPAIRER), null, Map.of()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> {
                    BizException be = (BizException) e;
                    assertThat(be.getCode()).isEqualTo(ResultCode.STATUS_CHANGED.getCode());
                    assertThat(be.getHttpStatus().value()).isEqualTo(409);
                });
    }

    @Test
    @DisplayName("成功流转应写流转日志")
    void successfulTransitionWritesLog() {
        stubSelect(order(OrderStatus.PENDING_REVIEW));
        stubUpdateSuccess();

        machine.fire(ORDER_ID, OrderAction.REVIEW_PASS, user(1L, RoleCode.MANAGER), "通过", Map.of());

        org.mockito.Mockito.verify(logMapper).insert(any(RepairOrderLog.class));
    }
}
