package com.campusfix.order.dto;

import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 预约与维修过程的请求 / 响应对象。
 */
public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    // ---------------------------------------------------------------- 预约

    /** 单个意向时段。 */
    public record SlotRequest(
            @NotNull(message = "请选择上门日期") LocalDate planDate,
            @NotNull(message = "请填写时段开始时间") LocalTime slotStart,
            @NotNull(message = "请填写时段结束时间") LocalTime slotEnd,
            @Size(max = 255, message = "备注不超过 255 字") String remark
    ) {
        public void validate() {
            if (!slotStart.isBefore(slotEnd)) {
                throw new BizException(ResultCode.PARAM_INVALID, "时段开始时间必须早于结束时间");
            }
            if (planDate.isBefore(LocalDate.now())) {
                throw new BizException(ResultCode.PARAM_INVALID, "上门日期不能早于今天");
            }
        }
    }

    /** 维修工一次给出 1–3 个时段。 */
    public record OfferRequest(
            @NotEmpty(message = "请至少提供一个可上门时段")
            @Size(max = 3, message = "一次最多提供 3 个时段")
            @Valid List<SlotRequest> slots
    ) {
    }

    public record ScheduleVO(
            Long id,
            Long orderId,
            Long repairerId,
            String repairerName,
            String planDate,
            String slotStart,
            String slotEnd,
            String status,
            String statusLabel,
            String proposedBy,
            String remark,
            /** 是否为改约产生的新提议 */
            boolean reschedule,
            /** 学生当前可选中 */
            boolean selectable,
            /** 学生当前可接受 */
            boolean acceptable
    ) {
    }

    // ---------------------------------------------------------------- 维修记录

    public record RecordRequest(
            @Size(max = 1000, message = "维修内容不超过 1000 字") String content,
            @Size(max = 500, message = "使用材料不超过 500 字") String material,
            @Size(max = 500, message = "维修结果不超过 500 字") String result,
            @Size(max = 500, message = "备注不超过 500 字") String remark,
            @Valid List<OrderDtos.ImageItem> images
    ) {
    }

    /** 提交维修结果：记录 + 维修后照片，提交后工单转「待确认」。 */
    public record CompleteRequest(
            @NotBlank(message = "请填写维修内容")
            @Size(max = 1000, message = "维修内容不超过 1000 字") String content,
            @Size(max = 500, message = "使用材料不超过 500 字") String material,
            @NotBlank(message = "请填写维修结果")
            @Size(max = 500, message = "维修结果不超过 500 字") String result,
            @Size(max = 500, message = "备注不超过 500 字") String remark,
            @Valid List<OrderDtos.ImageItem> images
    ) {
    }

    @Data
    public static class RecordVO {
        private Long id;
        private Long orderId;
        private Long repairerId;
        private String repairerName;
        private LocalDateTime arriveAt;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private String content;
        private String material;
        private String result;
        private String remark;
        /** 从开始到结束的分钟数，未完成时为 null */
        private Long durationMinutes;
    }
}
