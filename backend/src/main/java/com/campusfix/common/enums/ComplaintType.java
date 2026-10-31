package com.campusfix.common.enums;

import lombok.Getter;

/**
 * 投诉类型，与《系统设计说明书》§6.4.8 的枚举取值一致。
 */
@Getter
public enum ComplaintType {

    ATTITUDE("服务态度"),
    SLOW("响应拖沓"),
    QUALITY("维修质量"),
    OTHER("其他问题");

    private final String label;

    ComplaintType(String label) {
        this.label = label;
    }

    public static String labelOf(String name) {
        for (ComplaintType t : values()) {
            if (t.name().equals(name)) {
                return t.label;
            }
        }
        return name;
    }

    public static boolean isValid(String name) {
        for (ComplaintType t : values()) {
            if (t.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
