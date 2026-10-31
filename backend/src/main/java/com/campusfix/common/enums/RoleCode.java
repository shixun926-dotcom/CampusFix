package com.campusfix.common.enums;

/**
 * 角色编码，与 sys_role.code 一致。
 */
public final class RoleCode {

    public static final String STUDENT = "STUDENT";
    public static final String REPAIRER = "REPAIRER";
    public static final String MANAGER = "MANAGER";
    public static final String ADMIN = "ADMIN";

    /** 定时任务等系统行为在流转日志中的角色标识，不对应 sys_role 记录 */
    public static final String SYSTEM = "SYSTEM";

    private RoleCode() {
    }
}
