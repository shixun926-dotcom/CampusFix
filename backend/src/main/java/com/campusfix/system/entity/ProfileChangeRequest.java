package com.campusfix.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修工信息更改申请。
 *
 * <p>姓名 / 工号 / 所属区域为管理员导入生成、本人不可自助修改，
 * 发现错误时由维修工发起更改申请，经后勤管理员审核后落地。</p>
 */
@Data
@TableName("profile_change_request")
public class ProfileChangeRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 申请人（维修工） */
    private Long applicantId;
    /** 要更改的字段：real_name / staff_no / dept_name（仅这三项，手机号、头像走自助修改） */
    private String fieldKey;
    /** 字段中文名，如「姓名」「工号」 */
    private String fieldLabel;
    /** 原值 */
    private String oldValue;
    /** 申请的新值 */
    private String newValue;
    /** 申请理由 */
    private String reason;
    /** 见 ProfileChangeStatus */
    private String status;
    /** 审核人（后勤管理员） */
    private Long reviewerId;
    /** 打回/驳回原因（打回时必填） */
    private String rejectReason;
    private LocalDateTime reviewedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
