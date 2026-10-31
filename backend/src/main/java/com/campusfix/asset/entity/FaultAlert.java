package com.campusfix.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 高频故障预警（基线 §12.3）。
 *
 * <p>同一楼栋 + 最近 N 天 + 同一故障类型 + 工单数 ≥ 阈值 → 一条预警。
 * {@code uk_day_combo} 保证同楼栋同类型同窗口结束日只有一条，定时任务幂等。</p>
 */
@Data
@TableName("fault_alert")
public class FaultAlert {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buildingId;
    private Long categoryId;
    private Integer orderCount;
    private LocalDate windowStart;
    private LocalDate windowEnd;
    /** NEW / READ / HANDLED */
    private String status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
