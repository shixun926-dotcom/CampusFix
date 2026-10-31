package com.campusfix.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("sys_dept")
public class SysDept {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String code;
    private String leader;
    private String phone;
    /** REPAIR 维修部门 / ADMIN 行政 */
    private String type;
    private Integer sort;
    private Integer status;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private List<SysDept> children = new ArrayList<>();
}
