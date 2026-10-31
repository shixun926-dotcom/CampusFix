package com.campusfix.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campusfix.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户。学生用学号、教职工用工号登录，不开放自助注册。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;
    private String password;
    private String realName;
    private String studentNo;
    /** 班级（学生），如「计算机 2201」 */
    private String className;
    private String staffNo;
    private Long deptId;
    private String phone;
    private String avatar;
    private Integer creditScore;
    /** 1 正常 0 禁用 */
    private Integer status;
    /** 1 需强制改密 */
    private Integer pwdResetFlag;
    private java.time.LocalDateTime lastLoginAt;
}
