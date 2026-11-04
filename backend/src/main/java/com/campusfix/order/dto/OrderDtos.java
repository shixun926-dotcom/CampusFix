package com.campusfix.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单域请求与响应对象。
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    // ---------------------------------------------------------------- 创建

    /**
     * 提交报修。图片先经 {@code POST /files/upload} 拿到 objectKey，再随建单一起提交。
     */
    public record CreateRequest(
            @NotBlank(message = "报修标题不能为空")
            @Size(min = 4, max = 100, message = "标题需 4-100 字") String title,

            @NotBlank(message = "故障描述不能为空")
            @Size(min = 10, max = 1000, message = "故障描述需 10-1000 字") String description,

            @NotNull(message = "请选择故障类型") Long categoryId,

            @NotNull(message = "请选择报修位置") Long locationId,

            Long facilityId,

            @Min(value = 1, message = "紧急程度取值 1-3")
            @Max(value = 3, message = "紧急程度取值 1-3") Integer urgency,

            @NotEmpty(message = "请至少上传 1 张现场照片")
            @Size(max = 9, message = "最多上传 9 张照片")
            @Valid List<ImageItem> images,

            /** 学生必须确认隐私提示后才能提交 */
            Boolean privacyConfirmed,

            String reporterPhone
    ) {
    }

    public record ImageItem(
            @NotBlank(message = "图片 objectKey 不能为空") String objectKey,
            String stage,
            Integer sort,
            String fileName,
            Integer fileSize
    ) {
    }

    // ---------------------------------------------------------------- 查询

    @Data
    public static class Query {
        private long page = 1;
        private long size = 20;
        /** 多状态逗号分隔，如 PENDING,ASSIGNED */
        private String status;
        private Long categoryId;
        private Long buildingId;
        private Long deptId;
        private String keyword;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        /** mine = 与我相关（默认）；all = 全量（需 order:list:all）；pool = 抢单池 */
        private String scope;
    }

    // ---------------------------------------------------------------- 响应

    @Data
    public static class OrderVO {
        private Long id;
        private String orderNo;
        private String title;
        private String status;
        private String statusLabel;
        private Long categoryId;
        private String categoryName;
        private Long locationId;
        private String locationPath;
        private Long buildingId;
        private Integer urgency;
        private Integer priority;
        private Long reporterId;
        private String reporterName;
        private String reporterPhone;
        private Long assigneeId;
        private String assigneeName;
        private String assigneePhone;
        /** 维修工对外信息：头像 / 工号 / 所属区域（学生端工单详情可见） */
        private String assigneeAvatar;
        private String assigneeStaffNo;
        private String assigneeDeptName;
        private Long deptId;
        private String deptName;
        private Integer isWarranty;
        private Integer reworkFlag;
        private Integer reworkCount;
        private Integer autoAssigned;
        /** 是否由系统超时自动验收 */
        private Boolean autoConfirmed;
        private LocalDateTime planTime;
        private LocalDateTime createdAt;
        private LocalDateTime acceptedAt;
        private LocalDateTime finishedAt;
        private LocalDateTime confirmDeadline;
        /** 当前登录者在该工单上可执行的动作（前端据此显示按钮） */
        private List<String> actions;
    }

    @Data
    public static class OrderDetailVO extends OrderVO {
        private String description;
        private Long facilityId;
        private String rejectReason;
        private LocalDateTime reviewAt;
        private LocalDateTime arrivedAt;
        private LocalDateTime startedAt;
        private LocalDateTime confirmedAt;
        private List<ImageVO> images;
        /** 维修过程记录，未开工时为 null；预约时段另走 /orders/{id}/schedules */
        private ScheduleDtos.RecordVO record;
        /** 已提交的评价，未评价为 null */
        private EvaluationDtos.EvaluationVO evaluation;
        /** 当前登录者此刻能否评价：已完成 + 本人报修 + 尚未评价 */
        private Boolean canEvaluate;
        /** 该工单的投诉条数（含已终结） */
        private Integer complaintCount;
        /** 该工单是否存在未终结的投诉 */
        private Boolean hasOpenComplaint;
    }

    public record ImageVO(Long id, String stage, String objectKey, String url, Integer sort) {
    }

    public record LogVO(Long id, String fromStatus, String toStatus, String fromLabel, String toLabel,
                        String action, String operatorName, String operatorRole, String remark,
                        LocalDateTime createdAt) {
    }

    /** 流转结果，统一返回 id + 新状态，前端据此刷新。 */
    public record FireResult(Long id, String status, String statusLabel) {
    }

    /** 建单结果。 */
    public record CreateResult(Long id, String orderNo, String status, String statusLabel,
                               boolean warranty, String message) {
    }

    /** 指派 / 转派请求。 */
    public record AssignRequest(Long repairerId, String remark) {
    }

    /** 维修工日历：单日忙闲（已确认上门时段）。 */
    public record CalendarDay(String date, int count, List<CalendarSlot> slots) {
    }

    public record CalendarSlot(Long orderId, String orderTitle, String slotStart, String slotEnd) {
    }

    /** 维修工日历总览：未来 N 天已确认预约 + 在办数。 */
    public record CalendarVO(Long repairerId, int days, String windowStart, String windowEnd,
                             int wipCount, int confirmedCount, List<CalendarDay> items) {
    }

    /** 只带原因的操作请求（驳回、退单、取消、挂起等）。 */
    public record ReasonRequest(String reason) {
    }

    /** 审核通过可附备注。 */
    public record RemarkRequest(String remark) {
    }

    /** 位置树节点。 */
    public record LocationNode(Long id, Long parentId, Integer level, String name, String code,
                               String type, List<LocationNode> children) {
    }

    /** 故障类型。 */
    public record CategoryVO(Long id, String name, String code, Long deptId, String deptName) {
    }

    /** 待指派维修工（指派下拉用人少的排在前面）。 */
    public record RepairerVO(Long id, String realName, String staffNo, String phone,
                             Long deptId, String deptName, int wipCount, Integer creditScore) {
    }
}
