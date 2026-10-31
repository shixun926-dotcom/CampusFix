package com.campusfix.common.enums;

import lombok.Getter;

/**
 * 预约时段状态。
 *
 * <p>语义（基线 A15）：时段是「维修工可能上门的时间窗口」，不是排他占用，
 * 所以 {@code OFFERED} 允许同一维修工在相近时间给多个单子报时段，
 * 只有 {@code CONFIRMED} 才做重叠校验。</p>
 */
@Getter
public enum ScheduleStatus {

    /** 维修工已给出，等学生选择 */
    OFFERED("待选择"),
    /** 学生已选中，等维修工确认 */
    SELECTED("已选择"),
    /** 双方确认，工单转「处理中」 */
    CONFIRMED("已确认"),
    /** 作废（被改约、被拒绝、或同批未被选中） */
    CANCELLED("已作废");

    private final String label;

    ScheduleStatus(String label) {
        this.label = label;
    }
}
