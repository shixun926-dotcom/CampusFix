package com.campusfix.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campusfix.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 设施档案（基线 §9.2）。
 *
 * <p>编号规则「类型码-年份-序号」如 {@code AC-2026-00128}；
 * 状态 NORMAL / REPAIRING / SCRAPPED，其中 REPAIRING 与工单联动：
 * 工单进入 PROCESSING 置 REPAIRING，验收完成回 NORMAL。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("facility")
public class Facility extends BaseEntity {

    private String facilityNo;
    private String name;
    private Long typeId;
    private Long locationId;
    private LocalDate installDate;
    private String status;
    private String remark;
}
