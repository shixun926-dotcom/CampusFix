package com.campusfix.order.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.AcceptanceDtos;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.service.AcceptanceService;
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

/**
 * 确认验收与返修。
 *
 * <p>「确认 / 返修 / 申请延期」限报修人本人，故不标 `@PreAuthorize`
 * （学生没有 `order:handle` 这类职能权限），由 Service 校验归属；
 * 「审批延期」限管理员。</p>
 */
@Tag(name = "确认验收")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AcceptanceController {

    private final AcceptanceService acceptanceService;

    @Operation(summary = "验收概况（倒计时、延期记录）")
    @GetMapping("/orders/{id}/acceptance")
    public Result<AcceptanceDtos.AcceptanceVO> info(@PathVariable Long id) {
        return Result.ok(acceptanceService.acceptanceInfo(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "报修人确认完成（解锁评价）")
    @PostMapping("/orders/{id}/confirm")
    @OperLog(module = "工单", action = "确认完成")
    public Result<OrderDtos.FireResult> confirm(@PathVariable Long id,
                                                @RequestBody(required = false) OrderDtos.RemarkRequest body) {
        return Result.ok(acceptanceService.confirm(id, body == null ? null : body.remark(),
                CurrentUserHolder.get()));
    }

    @Operation(summary = "报修人申请返修（退回抢单池，需重新预约）")
    @PostMapping("/orders/{id}/rework")
    @OperLog(module = "工单", action = "申请返修")
    public Result<OrderDtos.FireResult> rework(@PathVariable Long id,
                                               @Valid @RequestBody AcceptanceDtos.ReworkRequest body) {
        return Result.ok(acceptanceService.rework(id, body.reason(), CurrentUserHolder.get()));
    }

    @Operation(summary = "报修人申请延期确认")
    @PostMapping("/orders/{id}/extension")
    @OperLog(module = "工单", action = "申请延期")
    public Result<AcceptanceDtos.ExtensionVO> applyExtension(@PathVariable Long id,
                                                             @Valid @RequestBody AcceptanceDtos.ExtensionRequest body) {
        return Result.ok(acceptanceService.applyExtension(id, body.reason(), CurrentUserHolder.get()));
    }

    @Operation(summary = "管理员审批延期申请")
    @PutMapping("/extensions/{extensionId}/handle")
    @PreAuthorize("hasAnyAuthority('order:review','order:assign')")
    @OperLog(module = "工单", action = "审批延期")
    public Result<AcceptanceDtos.ExtensionVO> handle(@PathVariable Long extensionId,
                                                     @Valid @RequestBody AcceptanceDtos.HandleExtensionRequest body) {
        return Result.ok(acceptanceService.handleExtension(extensionId, body, CurrentUserHolder.get()));
    }

    @Operation(summary = "手动触发一次超时自动验收（运维用）")
    @PostMapping("/orders/auto-confirm")
    @PreAuthorize("hasAuthority('order:assign')")
    public Result<Integer> autoConfirm() {
        return Result.ok(acceptanceService.autoConfirm());
    }
}
