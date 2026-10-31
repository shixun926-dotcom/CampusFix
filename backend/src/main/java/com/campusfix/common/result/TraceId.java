package com.campusfix.common.result;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID，注入 MDC，日志与响应体共用。
 */
public final class TraceId {

    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "" : id;
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
