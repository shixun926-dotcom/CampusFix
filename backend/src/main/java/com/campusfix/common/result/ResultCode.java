package com.campusfix.common.result;

import lombok.Getter;

/**
 * 业务错误码。
 * 分段规则（见《系统设计说明书》§5.2）：
 * 1xxx 认证与参数、2xxx 权限、3xxx 业务状态、4xxx 资源、5xxx 系统。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),

    /* 1xxx 认证与参数 */
    UNAUTHORIZED(1001, "未登录或登录已失效"),
    TOKEN_EXPIRED(1002, "登录已过期，请重新登录"),
    PARAM_INVALID(1003, "参数校验失败"),
    LOGIN_FAILED(1004, "账号或密码错误"),
    ACCOUNT_DISABLED(1005, "账号已被禁用"),
    ACCOUNT_LOCKED(1006, "登录失败次数过多，请稍后再试"),
    PASSWORD_NEED_RESET(1007, "首次登录需修改密码"),
    OLD_PASSWORD_ERROR(1008, "原密码不正确"),

    /* 2xxx 权限 */
    FORBIDDEN(2001, "无操作权限"),
    DATA_FORBIDDEN(2002, "无权访问该数据"),

    /* 3xxx 业务状态 */
    STATUS_NOT_ALLOWED(3001, "当前状态不允许该操作"),
    ORDER_GRABBED(3002, "该工单已被他人接单"),
    SCHEDULE_CONFLICT(3003, "该时间段已被占用"),
    IMAGE_LIMIT(3004, "图片数量超出限制"),
    REPEAT_EVALUATION(3005, "该工单已评价"),
    CREDIT_LIMITED(3006, "信誉分不足，暂时无法操作"),
    PRIVACY_NOT_CONFIRMED(3007, "请先确认隐私提示"),
    ORDER_NOT_FINISHED(3008, "工单尚未完成维修"),
    FILE_TYPE_NOT_ALLOWED(3009, "不支持的文件类型"),
    NO_SCHEDULE_CONFIRMED(3010, "尚未确认预约时间"),
    COMPLAINT_HANDLED(3011, "该投诉已处理"),
    EXTENSION_EXISTS(3012, "已有待审批的延期申请"),
    STATUS_CHANGED(3013, "工单状态已变更，请刷新后重试"),
    CREDIT_LOW_FOR_GRAB(3014, "信誉分不足，暂时无法抢单"),
    NO_CANDIDATE_REPAIRER(3015, "没有可派单的维修人员"),
    COMPLAINT_EXISTS(3016, "该工单已有待处理的投诉"),

    /* 4xxx 资源 */
    NOT_FOUND(4001, "资源不存在"),
    ALREADY_DELETED(4002, "资源已删除"),
    DUPLICATE(4003, "数据已存在"),

    /* 5xxx 系统 */
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试"),
    STORAGE_ERROR(5001, "文件存储失败");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
