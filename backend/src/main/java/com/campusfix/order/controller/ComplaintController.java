package com.campusfix.order.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.EvaluationDtos;
import com.campusfix.order.service.ComplaintService;
import com.campusfix.system.log.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 投诉：提交、按工单查询、管理端列表与处理。
 */
@Tag(name = "投诉")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ComplaintController {

    private final ComplaintService complaintService;

    @Operation(summary = "提交投诉（报修人）")
    @PostMapping("/orders/{id}/complaint")
    @OperLog(module = "投诉", action = "提交投诉")
    public Result<EvaluationDtos.ComplaintVO> submit(@PathVariable Long id,
                                                     @Valid @RequestBody EvaluationDtos.ComplaintRequest req) {
        return Result.ok(complaintService.submit(id, req, CurrentUserHolder.get()));
    }

    @Operation(summary = "工单的投诉记录")
    @GetMapping("/orders/{id}/complaints")
    public Result<List<EvaluationDtos.ComplaintVO>> listByOrder(@PathVariable Long id) {
        return Result.ok(complaintService.listByOrder(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "投诉列表（按部门分片，附各状态计数与投诉率）")
    @GetMapping("/complaints")
    @PreAuthorize("hasAuthority('complaint:list')")
    public Result<EvaluationDtos.ComplaintPageVO> page(@ModelAttribute EvaluationDtos.ComplaintQuery query) {
        return Result.ok(complaintService.page(query, CurrentUserHolder.get()));
    }

    @Operation(summary = "处理投诉（成立则扣维修工 10 分）")
    @PutMapping("/complaints/{id}/handle")
    @PreAuthorize("hasAuthority('complaint:handle')")
    @OperLog(module = "投诉", action = "处理投诉")
    public Result<EvaluationDtos.ComplaintVO> handle(@PathVariable Long id,
                                                     @Valid @RequestBody EvaluationDtos.ComplaintHandleRequest req) {
        return Result.ok(complaintService.handle(id, req, CurrentUserHolder.get()));
    }
}
