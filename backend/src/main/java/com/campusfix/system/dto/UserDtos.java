package com.campusfix.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户模块 DTO。
 */
public final class UserDtos {

    private UserDtos() {
    }

    @Data
    public static class Query {
        private Integer page = 1;
        private Integer size = 20;
        private String keyword;
        private String roleCode;
        private Long deptId;
        private Integer status;
    }

    @Data
    public static class SaveRequest {
        private Long id;
        @NotBlank(message = "学号/工号不能为空")
        @Size(max = 30, message = "账号长度不能超过 30")
        private String username;
        @NotBlank(message = "姓名不能为空")
        private String realName;
        private String studentNo;
        private String className;
        private String staffNo;
        private Long deptId;
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
        private Integer status = 1;
        private List<String> roleCodes;
    }

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String realName;
        private String studentNo;
        private String className;
        private String staffNo;
        private Long deptId;
        private String deptName;
        private String phone;
        private String avatar;
        private Integer creditScore;
        private Integer status;
        private List<String> roleCodes;
        private List<String> roleNames;
        private LocalDateTime lastLoginAt;
        private LocalDateTime createdAt;
    }

    /**
     * 个人中心资料（维修工自助维护）。
     * 工号 / 所属区域为只读，由管理员在用户管理维护，此处仅透出。
     * avatar 为空表示「未更换头像，使用系统默认头像」。
     */
    @Data
    public static class ProfileVO {
        private Long id;
        private String username;
        private String realName;
        private String staffNo;
        private String phone;
        private String avatar;
        private String avatarUrl;
        private Long deptId;
        private String deptName;
        /** 是否已更换自定义头像（avatar 非空即 true） */
        private Boolean hasAvatar;
        /** 头像是否已逾期未更换（未更换且超过 avatar_deadline） */
        private Boolean avatarOverdue;
        /** 头像强制更换截止时间 */
        private LocalDateTime avatarDeadline;
    }

    /**
     * 个人中心保存请求。仅手机号允许用户自助修改；
     * 姓名 / 工号 / 所属区域由管理员导入时生成，头像走独立上传接口，
     * 三者均不接受（防御式忽略，不靠前端禁用保证）。
     */
    public record ProfileUpdateRequest(
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,
            /** 头像 objectKey：更换头像后一并提交；为 null/空 表示不更换 */
            String avatar) {
    }

    /**
     * 信息更改申请：针对姓名 / 工号 / 所属区域（这三项本人不可自助修改）。
     */
    public record ProfileChangeSubmitRequest(
            @NotBlank(message = "字段标识不能为空") String fieldKey,
            @NotBlank(message = "新值不能为空")
            @Size(max = 100, message = "新值不能超过 100 字") String newValue,
            @NotBlank(message = "请填写申请理由")
            @Size(max = 500, message = "申请理由不能超过 500 字") String reason) {
    }

    /** 审核信息更改申请（通过 / 打回）。 */
    public record ProfileChangeReviewRequest(
            @NotBlank(message = "审核结果不能为空") String status,
            /** 打回时必填 */
            String rejectReason) {
    }

    @Data
    public static class ProfileChangeVO {
        private Long id;
        private Long applicantId;
        private String applicantName;
        private String applicantStaffNo;
        private String fieldKey;
        private String fieldLabel;
        private String oldValue;
        private String newValue;
        private String reason;
        private String status;
        private String statusLabel;
        private Long reviewerId;
        private String reviewerName;
        private String rejectReason;
        private LocalDateTime reviewedAt;
        private LocalDateTime createdAt;
    }

    public record ResetPasswordRequest(String newPassword) {
    }

    /**
     * 手动新增单个学生：账号由学号自动生成，初始密码为系统默认值。
     */
    public record StudentCreateRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 50, message = "姓名不能超过 50 字") String realName,
            @NotBlank(message = "学号不能为空")
            @Pattern(regexp = "^[0-9A-Za-z]{4,30}$", message = "学号需为 4-30 位数字或字母") String studentNo,
            @NotBlank(message = "班级不能为空")
            @Size(max = 50, message = "班级不能超过 50 字") String className,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone) {
    }

    public record CreditAdjustRequest(Integer delta, String reason) {
    }
}
