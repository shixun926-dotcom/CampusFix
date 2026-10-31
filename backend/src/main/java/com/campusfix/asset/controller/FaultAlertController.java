package com.campusfix.asset.controller;

import com.campusfix.asset.service.FaultAlertService;
import com.campusfix.asset.service.FaultAlertService.FaultAlertVO;
import com.campusfix.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 高频故障预警：列表、未处理数、标记状态、手动扫描（运维）。
 */
@Tag(name = "故障预警")
@RestController
@RequestMapping("/api/v1/fault-alerts")
@RequiredArgsConstructor
public class FaultAlertController {

    private final FaultAlertService faultAlertService;

    @Operation(summary = "预警列表（附提示文案）")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('order:review','order:assign')")
    public Result<List<FaultAlertVO>> list(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(faultAlertService.list(status, limit));
    }

    @Operation(summary = "未处理预警数（横幅角标）")
    @GetMapping("/new-count")
    @PreAuthorize("hasAnyAuthority('order:review','order:assign')")
    public Result<Long> newCount() {
        return Result.ok(faultAlertService.newCount());
    }

    @Operation(summary = "标记预警状态（READ 已读 / HANDLED 已处理）")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('order:review','order:assign')")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        faultAlertService.updateStatus(id, body.getOrDefault("status", ""));
        return Result.ok(null);
    }

    @Operation(summary = "手动触发一次预警扫描（运维用）")
    @PostMapping("/scan")
    @PreAuthorize("hasAnyAuthority('order:review','order:assign')")
    public Result<Integer> scan() {
        return Result.ok(faultAlertService.scan());
    }
}
