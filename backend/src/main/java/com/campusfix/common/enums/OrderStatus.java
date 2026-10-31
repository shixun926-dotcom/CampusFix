package com.campusfix.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * 工单状态（10 个），见《系统设计说明书》§6.6 数据字典。
 * 状态流转的合法边由 {@code OrderStateMachine} 集中定义，禁止在业务代码中直接 setStatus。
 */
@Getter
public enum OrderStatus {

    PENDING_REVIEW("待审核", false),
    PENDING("待处理", false),
    ASSIGNED("已分配", false),
    ACCEPTED("已接单", false),
    PROCESSING("处理中", false),
    SUSPENDED("挂起", false),
    PENDING_CONFIRM("待确认", false),
    COMPLETED("已完成", true),
    REJECTED("已驳回", true),
    CANCELLED("已取消", true);

    private final String label;
    private final boolean terminal;

    OrderStatus(String label, boolean terminal) {
        this.label = label;
        this.terminal = terminal;
    }

    /** 在办工单（用于自动派单选人：在办数最少的优先）。 */
    public static Set<OrderStatus> wipStatuses() {
        return EnumSet.of(ASSIGNED, ACCEPTED, PROCESSING, SUSPENDED);
    }

    /** 未完结状态（管理员可取消的范围）。 */
    public static Set<OrderStatus> activeStatuses() {
        return EnumSet.of(PENDING_REVIEW, PENDING, ASSIGNED, ACCEPTED, PROCESSING, SUSPENDED, PENDING_CONFIRM);
    }

    public static OrderStatus of(String name) {
        return Arrays.stream(values())
                .filter(s -> s.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知工单状态：" + name));
    }
}
