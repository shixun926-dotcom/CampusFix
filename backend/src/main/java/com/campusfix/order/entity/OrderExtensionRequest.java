package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 延期确认申请（基线 A20 / §8.4）。
 *
 * <p>报修人在「待确认」阶段说明特殊情况，申请暂停自动验收倒计时；
 * 管理员审批通过后顺延 {@code confirm_deadline}，驳回则照常按超时处理。</p>
 */
@Data
@TableName("order_extension_request")
public class OrderExtensionRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long studentId;
    private String reason;
    /** PENDING / APPROVED / REJECTED */
    private String status;
    private Long handlerId;
    private String handleResult;
    private LocalDateTime handledAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
