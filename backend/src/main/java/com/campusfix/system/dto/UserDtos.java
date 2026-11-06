package com.campusfix.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /**
     * 编辑用户基本信息（部分更新）。
     *
     * <p>字段语义：{@code null} 表示「不修改」；空串表示「清除」（仅 phone / className）；
     * {@code deptId = 0} 表示「清除所属部门」。后端据此拼装动态 SQL，
     * 只更新本次提交且确有变化的列，避免全量覆盖误清空。</p>
     */
    @Data
    public static class BasicUpdateRequest {

        @Size(max = 50, message = "姓名不能超过 50 字")
        @Pattern(regexp = "^$|^[\\u4e00-\\u9fa5A-Za-z·\\s]{1,50}$", message = "姓名仅支持中文、字母、空格与·")
        private String realName;

        /** null=不修改；空串=清除 */
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;

        /** null=不修改；空串=清除（仅学生可能持有） */
        @Size(max = 50, message = "班级不能超过 50 字")
        private String className;

        /** null=不修改；0=清除；其余为部门 ID */
        private Long deptId;

        /** null=不修改；仅允许 0（禁用）/ 1（启用） */
        private Integer status;

        /** null=不修改；非 null 时整体替换角色集合 */
        private List<String> roleCodes;

        /** 可选乐观校验：传上次读取到的 updated_at，不一致则拒绝 */
        private LocalDateTime expectedUpdatedAt;
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

    /* ------------------------------ 删除 / 批量删除 ------------------------------ */

    /**
     * 批量删除请求。
     *
     * <p>服务端对每个 id 独立校验（不能删自己、不能删最后一个启用管理员、
     * 不能删仍有未完结工单的人），因此本接口**不做全有或全无**：
     * 能删的删掉，不能删的在 {@link DeleteResult#getFailed()} 中逐条返回原因。</p>
     */
    @Data
    public static class DeleteBatchRequest {

        @NotEmpty(message = "请选择要删除的成员")
        @Size(max = 200, message = "单次最多删除 200 名成员")
        private List<Long> ids;
    }

    /** 单条删除失败明细。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteFailure {
        private Long id;
        /** 姓名（账号），便于前端直接展示 */
        private String name;
        /** 跳过原因 */
        private String reason;
    }

    /** 批量删除结果：成功条数 + 跳过明细。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteResult {
        private int success;
        private List<DeleteFailure> failed;
    }
}
