package com.campusfix.stats.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.stats.dto.StatsDtos.Insight;
import com.campusfix.stats.dto.StatsDtos.Overview;
import com.campusfix.stats.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计。任何登录用户可访问 overview（数据按角色在 SQL 层收窄）；
 * insight 为管理端洞察（后勤本部门 / 管理员全局）。
 */
@Tag(name = "数据统计")
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "工作台总览（KPI、状态计数、完成数、时长、趋势、类型/楼栋 TOP5）")
    @GetMapping("/overview")
    public Result<Overview> overview() {
        return Result.ok(statsService.overview(CurrentUserHolder.get()));
    }

    @Operation(summary = "数据统计页洞察（维修工绩效排行、月度趋势、投诉汇总；后勤本部门 / 管理员全局）")
    @GetMapping("/insight")
    @PreAuthorize("hasAnyAuthority('order:review','order:assign')")
    public Result<Insight> insight() {
        return Result.ok(statsService.insight(CurrentUserHolder.get()));
    }
}
