package com.campusfix.order.controller;

import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.EvaluationDtos;
import com.campusfix.order.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价。
 *
 * <p>「提交评价」限本单报修人，故不标 {@code @PreAuthorize} —— 权限在 Service 里按归属判定，
 * 而不是按角色。管理员给办公室报的单同样能评价。</p>
 */
@Tag(name = "评价")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @Operation(summary = "提交评价（报修人，一单一次）")
    @PostMapping("/orders/{id}/evaluation")
    public Result<EvaluationDtos.EvaluationVO> evaluate(@PathVariable Long id,
                                                        @Valid @RequestBody EvaluationDtos.EvaluateRequest req) {
        return Result.ok(evaluationService.evaluate(id, req, CurrentUserHolder.get()));
    }

    @Operation(summary = "查看工单评价（未评价返回 null）")
    @GetMapping("/orders/{id}/evaluation")
    public Result<EvaluationDtos.EvaluationVO> get(@PathVariable Long id) {
        return Result.ok(evaluationService.getByOrder(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "维修工收到的评价列表")
    @GetMapping("/repairers/{id}/evaluations")
    public Result<PageResult<EvaluationDtos.EvaluationVO>> pageByRepairer(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(evaluationService.pageByRepairer(id, page, size, CurrentUserHolder.get()));
    }

    @Operation(summary = "维修工评价汇总（均分与好评率）")
    @GetMapping("/repairers/{id}/evaluation-summary")
    public Result<EvaluationDtos.EvaluationSummaryVO> summary(@PathVariable Long id) {
        return Result.ok(evaluationService.summary(id));
    }
}
