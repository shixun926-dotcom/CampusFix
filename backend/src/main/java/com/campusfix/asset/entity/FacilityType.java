package com.campusfix.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 设施类型（空调、给排水、网络设备……），报修分类之外的资产维度分类。
 *
 * <p>facility_type 表没有审计与逻辑删除列，因此不继承 BaseEntity。</p>
 */
@Data
@TableName("facility_type")
public class FacilityType {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private Integer sort;
    private Integer status;
}
