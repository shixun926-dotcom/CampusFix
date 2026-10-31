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
        private Integer creditScore;
        private Integer status;
        private List<String> roleCodes;
        private List<String> roleNames;
        private LocalDateTime lastLoginAt;
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
