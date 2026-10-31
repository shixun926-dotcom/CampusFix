package com.campusfix.order.dto;

import com.campusfix.common.result.PageResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 评价与投诉的请求 / 响应对象。
 */
public final class EvaluationDtos {

    private EvaluationDtos() {
    }

    /**
     * 提交评价。三项必填且各自 1–5 分。
     */
    public record EvaluateRequest(
            @NotNull(message = "请评价维修速度")
            @Min(value = 1, message = "维修速度评分为 1–5 分")
            @Max(value = 5, message = "维修速度评分为 1–5 分") Integer speedScore,

            @NotNull(message = "请评价服务态度")
            @Min(value = 1, message = "服务态度评分为 1–5 分")
            @Max(value = 5, message = "服务态度评分为 1–5 分") Integer attitudeScore,

            @NotNull(message = "请评价维修效果")
            @Min(value = 1, message = "维修效果评分为 1–5 分")
            @Max(value = 5, message = "维修效果评分为 1–5 分") Integer effectScore,

            @Size(max = 500, message = "评价内容不能超过 500 字") String content,

            Boolean anonymous) {
    }

    /**
     * 评价详情。
     *
     * <p>{@code anonymous=1} 时 {@code studentName} 返回「匿名用户」，后端直接处理，
     * 不把真实姓名交给前端再由前端遮挡 —— 那样抓包就能看到。</p>
     */
    @Data
    public static class EvaluationVO {
        private Long id;
        private Long orderId;
        private String orderNo;
        private Long studentId;
        private String studentName;
        private Long repairerId;
        private String repairerName;
        private Integer speedScore;
        private Integer attitudeScore;
        private Integer effectScore;
        private BigDecimal totalScore;
        private String content;
        private Boolean anonymous;
        private LocalDateTime createdAt;
    }

    /**
     * 维修工评价汇总（用于维修工画像与派单参考）。
     */
    @Data
    public static class EvaluationSummaryVO {
        private Long repairerId;
        private String repairerName;
        private Integer total;
        private BigDecimal avgTotal;
        private BigDecimal avgSpeed;
        private BigDecimal avgAttitude;
        private BigDecimal avgEffect;
        /** 5 分好评占比（totalScore ≥ 4.5） */
        private BigDecimal goodRate;
    }

    /* ------------------------------------------------------------ 投诉 */

    /**
     * 提交投诉。工单必须已进入「待确认」或「已完成」——服务已经发生才谈得上投诉。
     */
    public record ComplaintRequest(
            @NotBlank(message = "请选择投诉类型") String type,
            @NotBlank(message = "请填写投诉内容")
            @Size(max = 1000, message = "投诉内容不能超过 1000 字") String content) {
    }

    /**
     * 处理投诉。
     *
     * @param status 目标状态：PROCESSING 受理、RESOLVED 成立、REJECTED 不成立
     * @param result 处理意见，成立/不成立时必填
     */
    public record ComplaintHandleRequest(
            @NotBlank(message = "请选择处理结果") String status,
            @Size(max = 500, message = "处理意见不能超过 500 字") String result) {
    }

    @Data
    public static class ComplaintVO {
        private Long id;
        private Long orderId;
        private String orderNo;
        private String orderTitle;
        private Long studentId;
        private String studentName;
        private Long repairerId;
        private String repairerName;
        private String type;
        private String typeLabel;
        private String content;
        private String status;
        private String statusLabel;
        private Long handlerId;
        private String handlerName;
        private String handleResult;
        private LocalDateTime handledAt;
        private LocalDateTime createdAt;
    }

    /**
     * 投诉列表查询条件。
     */
    @Data
    public static class ComplaintQuery {
        private Integer page = 1;
        private Integer size = 20;
        private String status;
        private String type;
        /** 工单号 / 投诉内容 / 报修人姓名 */
        private String keyword;
    }

    /**
     * 投诉列表响应，附带各状态计数，前端做页签角标。
     */
    @Data
    public static class ComplaintPageVO {
        private PageResult<ComplaintVO> page;
        private long pendingCount;
        private long processingCount;
        private long resolvedCount;
        private long rejectedCount;
        /** 投诉率 = 投诉数 / (总工单数 + 10)，小样本平滑口径 */
        private BigDecimal complaintRate;
        private List<String> types;
    }
}
