package com.campusfix.order.statemachine;

import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.entity.RepairOrder;

import java.util.Collections;
import java.util.Map;

/**
 * 状态流转上下文：一次 {@code fire()} 调用所需的全部输入。
 *
 * <p>把「工单快照 + 操作人 + 备注 + 业务参数」打包成对象，避免转换表里的
 * 前置校验器与副作用各自散落地去取参数。</p>
 */
public class OrderContext {

    private final RepairOrder order;
    /** 系统触发的流转（定时任务）为 null */
    private final LoginUser operator;
    private final String remark;
    private final Map<String, Object> params;

    public OrderContext(RepairOrder order, LoginUser operator, String remark, Map<String, Object> params) {
        this.order = order;
        this.operator = operator;
        this.remark = remark;
        this.params = params == null ? Collections.emptyMap() : params;
    }

    public RepairOrder order() {
        return order;
    }

    public LoginUser operator() {
        return operator;
    }

    public String remark() {
        return remark == null ? "" : remark;
    }

    public Long orderId() {
        return order.getId();
    }

    public Long reporterId() {
        return order.getReporterId();
    }

    public Long assigneeId() {
        return order.getAssigneeId();
    }

    public Long operatorId() {
        return operator == null ? null : operator.getId();
    }

    public boolean isSystem() {
        return operator == null;
    }

    public boolean operatorIs(RepairOrderRole role) {
        if (operator == null) {
            return false;
        }
        return switch (role) {
            case REPORTER -> operator.getId().equals(order.getReporterId());
            case ASSIGNEE -> operator.getId().equals(order.getAssigneeId());
        };
    }

    public Object param(String key) {
        return params.get(key);
    }

    public Long paramLong(String key) {
        Object v = params.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : Long.valueOf(s);
    }

    /** 操作人在本工单中的相对身份，用于前置校验。 */
    public enum RepairOrderRole {
        REPORTER, ASSIGNEE
    }

    /** 系统操作在日志中的角色标识。 */
    public static final String SYSTEM_ROLE = RoleCode.SYSTEM;
}
