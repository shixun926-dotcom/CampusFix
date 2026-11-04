package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 滞留工单推送去重记录。
 *
 * <p>状态滞留巡检（StallAlertJob）每次扫描出「超过阈值仍停留在非终态」的工单后，
 * 给管理员推送站内信。为避免每天重复轰炸，同一工单在同一状态只推一次：
 * {@code uk_order_status(order_id, status)} 唯一索引保证幂等。</p>
 *
 * <p>当工单状态发生变化（被人推进 / 取消 / 驳回），该工单再次进入滞留时，
 * 因 status 已不同，会作为新记录重新推送——这正是期望行为。</p>
 */
@Data
@TableName("order_stall_alert")
public class OrderStallAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    /** 推送时的工单状态（枚举名），与 uk_order_status 联合去重 */
    private String status;

    /** 判定滞留时的最后更新时间（updated_at），便于追溯 */
    private LocalDateTime stallSince;

    /** 已推送次数（当前实现恒为 1，预留手动重推能力） */
    private Integer notifiedCount;

    private LocalDateTime lastNotifiedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
