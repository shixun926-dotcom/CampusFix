package com.campusfix.order.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.dto.ScheduleDtos;
import com.campusfix.order.service.RecordService;
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
 * 维修过程：到达 / 开始打点、维修记录、提交结果。
 */
@Tag(name = "维修过程")
@RestController
@RequestMapping("/api/v1/orders/{id}")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @Operation(summary = "查看维修记录（相关方可见）")
    @GetMapping("/record")
    public Result<ScheduleDtos.RecordVO> get(@PathVariable Long id) {
        return Result.ok(recordService.get(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "到达报修点打点")
    @PostMapping("/arrive")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "到达打点")
    public Result<ScheduleDtos.RecordVO> arrive(@PathVariable Long id) {
        return Result.ok(recordService.arrive(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "开始维修打点")
    @PostMapping("/start")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "开始维修打点")
    public Result<ScheduleDtos.RecordVO> start(@PathVariable Long id) {
        return Result.ok(recordService.start(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "挂起（必填原因）")
    @PostMapping("/suspend")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "挂起工单")
    public Result<ScheduleDtos.RecordVO> suspend(@PathVariable Long id,
                                                 @RequestBody OrderDtos.ReasonRequest body) {
        return Result.ok(recordService.suspend(id, body == null ? null : body.reason(), CurrentUserHolder.get()));
    }

    @Operation(summary = "恢复（挂起 → 处理中）")
    @PostMapping("/resume")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "恢复工单")
    public Result<ScheduleDtos.RecordVO> resume(@PathVariable Long id) {
        return Result.ok(recordService.resume(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "判定无法维修（必填原因，转已驳回）")
    @PostMapping("/unrepairable")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "判定无法维修")
    public Result<ScheduleDtos.RecordVO> unrepairable(@PathVariable Long id,
                                                      @RequestBody OrderDtos.ReasonRequest body) {
        return Result.ok(recordService.unrepairable(id, body == null ? null : body.reason(),
                CurrentUserHolder.get()));
    }

    @Operation(summary = "保存维修记录与维修前照片")
    @PutMapping("/record")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "保存维修记录")
    public Result<ScheduleDtos.RecordVO> save(@PathVariable Long id,
                                              @Valid @RequestBody ScheduleDtos.RecordRequest body) {
        return Result.ok(recordService.save(id, body, CurrentUserHolder.get()));
    }

    @Operation(summary = "提交维修结果（工单转待确认）")
    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('order:handle')")
    @OperLog(module = "工单", action = "提交维修结果")
    public Result<ScheduleDtos.RecordVO> complete(@PathVariable Long id,
                                                  @Valid @RequestBody ScheduleDtos.CompleteRequest body) {
        return Result.ok(recordService.complete(id, body, CurrentUserHolder.get()));
    }
}
