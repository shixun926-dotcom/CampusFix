package com.campusfix.order.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.dto.ScheduleDtos;
import com.campusfix.order.service.ScheduleService;
import com.campusfix.system.log.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 维修预约：维修工给时段 → 学生选择 → 维修工确认 → 改约。
 *
 * <p>「给时段」与「确认」限维修工（`order:handle`）；「选择 / 接受 / 拒绝改约」只要求登录，
 * 由 Service 校验是否为报修人本人 —— 学生没有 `order:handle` 权限，不能用工号维度的注解。</p>
 */
@Tag(name = "维修预约")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "预约时段列表（相关方可见）")
    @GetMapping("/orders/{id}/schedules")
    public Result<List<ScheduleDtos.ScheduleVO>> list(@PathVariable Long id) {
        return Result.ok(scheduleService.list(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "维修工提供可上门时段（1–3 个）")
    @PostMapping("/orders/{id}/schedules")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "提供预约时段")
    public Result<List<ScheduleDtos.ScheduleVO>> offer(@PathVariable Long id,
                                                       @Valid @RequestBody ScheduleDtos.OfferRequest body) {
        return Result.ok(scheduleService.offer(id, body, CurrentUserHolder.get()));
    }

    @Operation(summary = "报修人选择时段")
    @PutMapping("/schedules/{scheduleId}/select")
    @OperLog(module = "工单", action = "选择预约时段")
    public Result<List<ScheduleDtos.ScheduleVO>> select(@PathVariable Long scheduleId) {
        return Result.ok(scheduleService.select(scheduleId, CurrentUserHolder.get()));
    }

    @Operation(summary = "维修工确认时段（工单转处理中）")
    @PutMapping("/schedules/{scheduleId}/confirm")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "确认预约")
    public Result<List<ScheduleDtos.ScheduleVO>> confirm(@PathVariable Long scheduleId) {
        return Result.ok(scheduleService.confirm(scheduleId, CurrentUserHolder.get()));
    }

    @Operation(summary = "维修工发起改约")
    @PostMapping("/orders/{id}/reschedule")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "发起改约")
    public Result<List<ScheduleDtos.ScheduleVO>> reschedule(@PathVariable Long id,
                                                            @Valid @RequestBody ScheduleDtos.SlotRequest body) {
        return Result.ok(scheduleService.reschedule(id, body, CurrentUserHolder.get()));
    }

    @Operation(summary = "报修人接受改约")
    @PutMapping("/schedules/{scheduleId}/accept")
    @OperLog(module = "工单", action = "接受改约")
    public Result<List<ScheduleDtos.ScheduleVO>> accept(@PathVariable Long scheduleId) {
        return Result.ok(scheduleService.acceptReschedule(scheduleId, CurrentUserHolder.get()));
    }

    @Operation(summary = "报修人拒绝改约（工单回抢单池重新分配）")
    @PutMapping("/schedules/{scheduleId}/reject")
    @OperLog(module = "工单", action = "拒绝改约")
    public Result<List<ScheduleDtos.ScheduleVO>> reject(@PathVariable Long scheduleId,
                                                        @RequestBody(required = false) OrderDtos.ReasonRequest body) {
        return Result.ok(scheduleService.rejectReschedule(scheduleId,
                body == null ? null : body.reason(), CurrentUserHolder.get()));
    }
}
