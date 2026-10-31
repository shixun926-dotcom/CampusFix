package com.campusfix.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * 认证相关 DTO 集合。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入学号或工号") String username,
            @NotBlank(message = "请输入密码") String password) {
    }

    public record RefreshRequest(@NotBlank(message = "refreshToken 不能为空") String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 8, max = 32, message = "密码长度需在 8-32 位之间") String newPassword) {
    }

    public record LoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            Long userId,
            String username,
            String realName,
            Set<String> roles,
            Set<String> permissions,
            Integer creditScore,
            boolean pwdResetFlag) {
    }

    public record UserProfile(
            Long userId,
            String username,
            String realName,
            Long deptId,
            Integer creditScore,
            Set<String> roles,
            Set<String> permissions,
            List<MenuNode> menus) {
    }

    public record MenuNode(Long id, Long parentId, String code, String name, String type, String path, String icon) {
    }
}
