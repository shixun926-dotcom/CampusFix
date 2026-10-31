package com.campusfix.common.security;

import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.Set;

/**
 * 当前登录用户访问入口。业务代码统一通过此处取用户，不直接操作 SecurityContext。
 */
public final class CurrentUserHolder {

    private CurrentUserHolder() {
    }

    public static LoginUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long id() {
        return get().getId();
    }

    public static Long deptId() {
        return get().getDeptId();
    }

    public static Set<String> roles() {
        LoginUser user = get();
        return user.getRoles() == null ? Collections.emptySet() : user.getRoles();
    }

    public static boolean hasRole(String role) {
        return get().hasRole(role);
    }

    public static boolean isAdmin() {
        return hasRole(RoleCode.ADMIN);
    }

    public static boolean isManager() {
        return hasRole(RoleCode.MANAGER);
    }

    public static boolean isRepairer() {
        return hasRole(RoleCode.REPAIRER);
    }

    public static boolean isStudent() {
        return hasRole(RoleCode.STUDENT);
    }

    /** 后勤管理员或系统管理员。 */
    public static boolean isManagerOrAdmin() {
        return isManager() || isAdmin();
    }
}
