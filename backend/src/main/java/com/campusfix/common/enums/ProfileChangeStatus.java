package com.campusfix.common.enums;

import lombok.Getter;

/**
 * 信息更改申请状态。
 *
 * <p>维修工提交更改申请 → 后勤管理员审核 → 通过（落地更改）/ 打回（附原因，通知申请人）。
 * 姓名 / 工号 / 所属区域为管理员导入生成，出现错误时走本流程更正，避免自助直改。</p>
 */
@Getter
public enum ProfileChangeStatus {

    PENDING("待审核"),
    APPROVED("已通过"),
    REJECTED("已打回");

    private final String label;

    ProfileChangeStatus(String label) {
        this.label = label;
    }

    public static ProfileChangeStatus of(String name) {
        for (ProfileChangeStatus s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return PENDING;
    }

    /** 已终结，不能再审 */
    public boolean isClosed() {
        return this == APPROVED || this == REJECTED;
    }
}
