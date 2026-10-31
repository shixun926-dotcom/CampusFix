package com.campusfix.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一响应体：{ code, message, data, traceId }。
 * 见《系统设计说明书》§5.2。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMessage("ok");
        r.setData(data);
        r.setTraceId(TraceId.current());
        return r;
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return fail(rc.getCode(), rc.getMessage());
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        r.setTraceId(TraceId.current());
        return r;
    }
}
