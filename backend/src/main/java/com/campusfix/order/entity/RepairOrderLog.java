package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单状态流转流水。
 *
 * <p>这是「进度时间轴」「处理时长统计」「责任追溯」的唯一数据源，
 * 任何一次状态变更都必须写一条，不能只依赖主表的当前状态。</p>
 */
@Data
@TableName("repair_order_log")
public class RepairOrderLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    /** 创建工单时为 null */
    private String fromStatus;
    private String toStatus;
    private String action;
    private Long operatorId;
    private String operatorRole;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
