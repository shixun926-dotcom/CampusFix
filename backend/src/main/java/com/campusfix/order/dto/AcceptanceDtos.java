package com.campusfix.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 确认验收、返修、延期申请的请求与响应。
 */
public final class AcceptanceDtos {

    private AcceptanceDtos() {
    }

    /** 申请返修：基线要求必填原因，便于追溯与统计。 */
    public record ReworkRequest(
            @NotBlank(message = "请说明返修原因")
            @Size(max = 500, message = "返修原因不超过 500 字") String reason
    ) {
    }

    /** 申请延期确认：说明特殊情况（出差、住院等）。 */
    public record ExtensionRequest(
            @NotBlank(message = "请说明申请延期的原因")
            @Size(max = 500, message = "原因不超过 500 字") String reason
    ) {
    }

    /** 管理员审批延期。 */
    public record HandleExtensionRequest(
            @NotNull(message = "请选择通过或驳回") Boolean approve,
            @Size(max = 500, message = "审批意见不超过 500 字") String result,
            /** 通过时顺延的天数，不传则用系统默认值 */
            Integer extraDays
    ) {
    }

    @Data
    public static class ExtensionVO {
        private Long id;
        private Long orderId;
        private Long studentId;
        private String studentName;
        private String reason;
        private String status;
        private String statusLabel;
        private Long handlerId;
        private String handlerName;
        private String handleResult;
        private LocalDateTime handledAt;
        private LocalDateTime createdAt;
    }

    /**
     * 验收概况：前端用它显示倒计时、延期入口与审批框。
     */
    @Data
    public static class AcceptanceVO {
        private Long orderId;
        private String status;
        private String statusLabel;
        /** 自动验收截止时间 */
        private LocalDateTime confirmDeadline;
        /** 剩余小时数，负数表示已超期；无截止时间时为 null */
        private Long hoursLeft;
        private Boolean overdue;
        private Boolean autoConfirmed;
        /** 系统默认的自动验收天数与延期顺延天数，供前端提示文案 */
        private Integer autoConfirmDays;
        private Integer extensionDays;
        /** 是否存在待审批的延期申请 */
        private Boolean hasPendingExtension;
        private List<ExtensionVO> extensions;
    }
}
