package com.campusfix.common.exception;

import com.campusfix.common.result.ResultCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常。全局异常处理器统一转换为 Result。
 *
 * <p>默认返回 HTTP 200 + 业务码，便于前端统一在响应体里判断；
 * 但对于「并发冲突」这类需要网关 / 浏览器也能识别的语义，可指定
 * {@link HttpStatus#CONFLICT}，例如抢单被他人抢先。</p>
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(ResultCode rc) {
        this(rc.getCode(), rc.getMessage(), HttpStatus.OK);
    }

    public BizException(ResultCode rc, String message) {
        this(rc.getCode(), message, HttpStatus.OK);
    }

    public BizException(int code, String message) {
        this(code, message, HttpStatus.OK);
    }

    public BizException(int code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus == null ? HttpStatus.OK : httpStatus;
    }

    public static BizException of(ResultCode rc) {
        return new BizException(rc);
    }

    public static BizException of(ResultCode rc, String message) {
        return new BizException(rc, message);
    }

    /** 409：并发冲突（抢单失败、状态已被他人推进）。 */
    public static BizException conflict(ResultCode rc, String message) {
        return new BizException(rc.getCode(), message, HttpStatus.CONFLICT);
    }
}
