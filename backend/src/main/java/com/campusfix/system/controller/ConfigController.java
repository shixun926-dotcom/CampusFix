package com.campusfix.system.controller;

import com.campusfix.common.result.Result;
import com.campusfix.system.entity.SysConfig;
import com.campusfix.system.log.OperLog;
import com.campusfix.system.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "系统参数")
@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final SystemConfigService configService;

    @Operation(summary = "参数列表")
    @GetMapping
    @PreAuthorize("hasAuthority('config:list')")
    public Result<List<SysConfig>> list() {
        return Result.ok(configService.listAll());
    }

    @Operation(summary = "修改参数")
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('config:edit')")
    @OperLog(module = "系统配置", action = "修改参数")
    public Result<Void> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        configService.update(key, body.get("value"));
        return Result.ok();
    }
}
