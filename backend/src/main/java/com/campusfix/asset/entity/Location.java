package com.campusfix.asset.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 位置树节点：校区(1) → 区域(2) → 楼栋(3) → 楼层(4) → 房间(5)。
 *
 * <p>结构化位置是「哪栋楼故障最多」「重复故障分析」的前提，
 * 不允许退化成自由文本。</p>
 */
@Data
@TableName("location")
public class Location {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private Integer level;
    private String name;
    private String code;
    /** CAMPUS / AREA / BUILDING / FLOOR / ROOM */
    private String type;
    private Integer sort;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
