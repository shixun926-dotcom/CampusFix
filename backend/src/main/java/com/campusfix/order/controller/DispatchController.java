package com.campusfix.order.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.service.DispatchService;
import com.campusfix.system.log.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 派单分配：指派、转派、抢单、接单、退单。
 */
@Tag(name = "派单分配")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchService dispatchService;

    @Operation(summary = "指派工单")
    @PostMapping("/orders/{id}/assign")
    @PreAuthorize("hasAuthority('order:assign')")
    @OperLog(module = "工单", action = "指派")
    public Result<OrderDtos.FireResult> assign(@PathVariable Long id,
                                               @RequestBody OrderDtos.AssignRequest body) {
        return Result.ok(dispatchService.assign(id, body.repairerId(), body.remark(), CurrentUserHolder.get()));
    }

    @Operation(summary = "转派工单")
    @PostMapping("/orders/{id}/transfer")
    @PreAuthorize("hasAuthority('order:assign')")
    @OperLog(module = "工单", action = "转派")
    public Result<OrderDtos.FireResult> transfer(@PathVariable Long id,
                                                 @RequestBody OrderDtos.AssignRequest body) {
        return Result.ok(dispatchService.transfer(id, body.repairerId(), body.remark(), CurrentUserHolder.get()));
    }

    @Operation(summary = "抢单（并发冲突返回 409）")
    @PostMapping("/orders/{id}/grab")
    @PreAuthorize("hasAuthority('order:grab')")
    @OperLog(module = "工单", action = "抢单")
    public Result<OrderDtos.FireResult> grab(@PathVariable Long id) {
        return Result.ok(dispatchService.grab(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "确认接单")
    @PostMapping("/orders/{id}/accept")
    @OperLog(module = "工单", action = "确认接单")
    public Result<OrderDtos.FireResult> accept(@PathVariable Long id) {
        return Result.ok(dispatchService.accept(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "退单（回到抢单池，扣信誉分）")
    @PostMapping("/orders/{id}/return")
    @OperLog(module = "工单", action = "退单")
    public Result<OrderDtos.FireResult> returnOrder(@PathVariable Long id,
                                                    @RequestBody(required = false) OrderDtos.ReasonRequest body) {
        return Result.ok(dispatchService.returnOrder(id, body == null ? null : body.reason(),
                CurrentUserHolder.get()));
    }

    @Operation(summary = "可指派维修工（在办少的排前面）")
    @GetMapping("/repairers")
    @PreAuthorize("hasAuthority('order:assign')")
    public Result<List<OrderDtos.RepairerVO>> repairers() {
        return Result.ok(dispatchService.listRepairers());
    }

    @Operation(summary = "维修工日历（未来 N 天已确认上门时段，指派/转派看忙闲；本人亦可查）")
    @GetMapping("/repairers/{id}/calendar")
    @PreAuthorize("hasAnyAuthority('order:assign','order:handle')")
    public Result<OrderDtos.CalendarVO> calendar(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "7") int days) {
        return Result.ok(dispatchService.calendar(id, days, CurrentUserHolder.get()));
    }

    @Operation(summary = "手动触发一次超时自动派单（运维用）")
    @PostMapping("/orders/auto-assign")
    @PreAuthorize("hasAuthority('order:assign')")
    public Result<Integer> autoAssign() {
        return Result.ok(dispatchService.autoAssign());
    }
}
