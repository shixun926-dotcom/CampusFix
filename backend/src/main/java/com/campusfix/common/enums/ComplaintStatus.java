package com.campusfix.common.enums;

import lombok.Getter;

/**
 * 投诉处理状态。
 *
 * <p>{@code RESOLVED} 表示投诉成立，触发维修工扣分；{@code REJECTED} 表示不成立，不扣分。
 * 扣分与否只取决于最终状态，不看处理意见文案，避免口径漂移。</p>
 */
@Getter
public enum ComplaintStatus {

    PENDING("待受理"),
    /** 处理中：已受理但需要进一步核实 */
    PROCESSING("处理中"),
    RESOLVED("已成立"),
    REJECTED("不成立");

    private final String label;

    ComplaintStatus(String label) {
        this.label = label;
    }

    public static ComplaintStatus of(String name) {
        for (ComplaintStatus s : values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return PENDING;
    }

    /** 已终结，不能再改 */
    public boolean isClosed() {
        return this == RESOLVED || this == REJECTED;
    }
}
