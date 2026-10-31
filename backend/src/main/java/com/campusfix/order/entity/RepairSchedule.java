package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 维修预约时段。表无逻辑删除列，故不继承 BaseEntity。
 *
 * <p>一次预约是「一批时段」：维修工给出 1–3 个 OFFERED，学生选一个转 SELECTED，
 * 其余同批自动 CANCELLED；维修工确认后转 CONFIRMED 并回写工单 {@code plan_time}。</p>
 */
@Data
@TableName("repair_schedule")
public class RepairSchedule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long repairerId;
    private LocalDate planDate;
    private LocalTime slotStart;
    private LocalTime slotEnd;
    /** 见 ScheduleStatus */
    private String status;
    /** REPAIRER 维修工给出 / STUDENT 学生反向提议 */
    private String proposedBy;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public LocalDateTime planTime() {
        return planDate == null || slotStart == null ? null : planDate.atTime(slotStart);
    }
}
