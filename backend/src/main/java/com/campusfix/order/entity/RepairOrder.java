package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.campusfix.common.entity.BaseEntity;
import com.campusfix.common.enums.OrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单主表。
 *
 * <p>{@code status} 用字符串存枚举名（与《系统设计说明书》§6.1 约定一致）；
 * 业务代码不要直接 setStatus，统一走 {@code OrderStateMachine.fire()}。
 * {@code version} 是乐观锁，所有流转都靠它做 CAS。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("repair_order")
public class RepairOrder extends BaseEntity {

    private String orderNo;
    private Long reporterId;
    private String reporterPhone;
    private String title;
    private String description;
    private Long categoryId;
    private Long facilityId;
    private Long locationId;
    /** 位置全路径快照，如「主校区 / 宿舍区 / 1号宿舍楼 / 2层 / 201」 */
    private String locationPath;
    /** 所属楼栋，冗余字段，供区域统计与重复故障分析 */
    private Long buildingId;
    /** 1 普通 2 紧急 3 非常紧急 */
    private Integer urgency;
    private Integer priority;
    private String status;
    private Long deptId;
    private Long assigneeId;
    @TableField("auto_assigned")
    private Integer autoAssigned;
    @TableField("is_warranty")
    private Integer isWarranty;
    private Long parentOrderId;
    @TableField("rework_flag")
    private Integer reworkFlag;
    private Integer reworkCount;
    private Long reviewBy;
    /** 审核通过时间，24h 自动派单的倒计时起点 */
    private LocalDateTime reviewAt;
    private String rejectReason;
    private LocalDateTime acceptedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime startedAt;
    /** 维修完成时间，统计口径基准 */
    private LocalDateTime finishedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime suspendStartAt;
    private Integer suspendMinutes;
    /** 自动验收截止时间 */
    private LocalDateTime confirmDeadline;
    @TableField("auto_confirmed")
    private Integer autoConfirmed;
    /** 确认的上门时间 */
    private LocalDateTime planTime;

    @Version
    private Integer version;

    public OrderStatus statusEnum() {
        return OrderStatus.of(status);
    }

    public boolean isStatus(OrderStatus s) {
        return s != null && s.name().equals(status);
    }
}
