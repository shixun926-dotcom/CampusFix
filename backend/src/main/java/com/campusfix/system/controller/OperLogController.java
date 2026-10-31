package com.campusfix.system.controller;

import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.Result;
import com.campusfix.system.log.OperLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 操作日志查询（基线 §3.2：后勤管理员 / 系统管理员可见，权限码 log:list）。
 */
@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/v1/oper-logs")
@RequiredArgsConstructor
public class OperLogController {

    private final OperLogService operLogService;

    @Operation(summary = "操作日志分页（按用户 / 模块 / 状态 / 时间筛选）")
    @GetMapping
    @PreAuthorize("hasAuthority('log:list')")
    public Result<PageResult<OperLogService.OperLogVO>> page(OperLogService.Query query) {
        return Result.ok(operLogService.page(query));
    }

    @Operation(summary = "模块下拉列表（去重）")
    @GetMapping("/modules")
    @PreAuthorize("hasAuthority('log:list')")
    public Result<List<String>> modules() {
        return Result.ok(operLogService.modules());
    }
}
