package com.campusfix.order.controller;

import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.service.OrderQueryService;
import com.campusfix.order.service.OrderService;
import com.campusfix.system.log.OperLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 报修工单：提交、查询、审核、取消。
 *
 * <p>状态流转都不在这里拼逻辑，统一交给 {@code OrderStateMachine}；
 * Controller 只做参数绑定与权限声明。</p>
 */
@Tag(name = "报修工单")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService queryService;

    @Operation(summary = "提交报修（全角色）")
    @PostMapping
    @OperLog(module = "工单", action = "提交报修")
    public Result<OrderDtos.CreateResult> create(@Valid @RequestBody OrderDtos.CreateRequest req) {
        return Result.ok(orderService.create(req, CurrentUserHolder.get()));
    }

    @Operation(summary = "工单列表（按角色过滤：mine 与我相关 / all 全部）")
    @GetMapping
    public Result<PageResult<OrderDtos.OrderVO>> page(OrderDtos.Query query) {
        return Result.ok(queryService.page(query, CurrentUserHolder.get()));
    }

    @Operation(summary = "抢单池（全部待处理工单）")
    @GetMapping("/pool")
    @PreAuthorize("hasAuthority('order:grab')")
    public Result<PageResult<OrderDtos.OrderVO>> pool(OrderDtos.Query query) {
        query.setScope("pool");
        return Result.ok(queryService.page(query, CurrentUserHolder.get()));
    }

    @Operation(summary = "工单详情")
    @GetMapping("/{id}")
    public Result<OrderDtos.OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(queryService.detail(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "流转时间轴")
    @GetMapping("/{id}/logs")
    public Result<List<OrderDtos.LogVO>> logs(@PathVariable Long id) {
        return Result.ok(queryService.logs(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "审核通过")
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('order:review')")
    @OperLog(module = "工单", action = "审核通过")
    public Result<OrderDtos.FireResult> review(@PathVariable Long id,
                                               @RequestBody(required = false) OrderDtos.RemarkRequest body) {
        String remark = body == null ? null : body.remark();
        return Result.ok(orderService.review(id, remark, CurrentUserHolder.get()));
    }

    @Operation(summary = "审核驳回")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('order:review')")
    @OperLog(module = "工单", action = "审核驳回")
    public Result<OrderDtos.FireResult> reject(@PathVariable Long id,
                                               @RequestBody OrderDtos.ReasonRequest body) {
        return Result.ok(orderService.reject(id, body == null ? null : body.reason(), CurrentUserHolder.get()));
    }

    @Operation(summary = "取消工单（仅管理员）")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('order:cancel')")
    @OperLog(module = "工单", action = "取消工单")
    public Result<OrderDtos.FireResult> cancel(@PathVariable Long id,
                                               @RequestBody(required = false) OrderDtos.ReasonRequest body) {
        return Result.ok(orderService.cancel(id, body == null ? null : body.reason(), CurrentUserHolder.get()));
    }
}
