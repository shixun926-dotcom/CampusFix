package com.campusfix.order.statemachine;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.order.entity.RepairOrder;

import java.util.Set;
import java.util.function.Predicate;

/**
 * 一条合法的状态转换边。
 *
 * <p>包含四要素：目标状态、允许角色、前置校验、副作用。
 * 副作用只负责「额外要落库的字段」，目标状态与 version 由状态机统一写入，
 * 避免各处自己拼 SQL 造成状态与字段不一致。</p>
 *
 * @param froms         允许的来源状态（退单、取消等一对多场景用集合表达）
 * @param guard         前置校验，返回 false 时抛出 {@code guardMessage}
 * @param guardMessage  校验失败提示
 * @param effect        额外字段写入，可为 null
 */
public record Transition(
        OrderAction action,
        Set<OrderStatus> froms,
        OrderStatus to,
        Set<String> roles,
        Predicate<OrderContext> guard,
        String guardMessage,
        TransitionEffect effect) {

    public boolean appliesTo(OrderStatus from) {
        return froms.contains(from);
    }

    public boolean roleAllowed(Set<String> operatorRoles) {
        if (roles.isEmpty()) {
            return true;
        }
        return roles.stream().anyMatch(operatorRoles::contains);
    }

    /** 该转换的主要责任角色，用于流转日志记录。 */
    public String primaryRole(Set<String> operatorRoles) {
        // roles 为空表示「不按角色限制、只按归属校验」，日志里记操作人自己的角色
        if (roles.isEmpty()) {
            return operatorRoles.stream().findFirst().orElse("");
        }
        return roles.stream().filter(operatorRoles::contains).findFirst()
                .orElse(roles.iterator().next());
    }

    @FunctionalInterface
    public interface TransitionEffect {
        void apply(OrderContext ctx, LambdaUpdateWrapper<RepairOrder> update);
    }
}
