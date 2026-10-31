package com.campusfix.system.controller;

import com.campusfix.common.result.Result;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.service.DeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "组织与部门")
@RestController
@RequestMapping("/api/v1/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @Operation(summary = "部门树")
    @GetMapping("/tree")
    public Result<List<SysDept>> tree() {
        return Result.ok(deptService.tree());
    }

    @Operation(summary = "部门列表")
    @GetMapping
    public Result<List<SysDept>> list() {
        return Result.ok(deptService.listAll());
    }

    @Operation(summary = "新增部门")
    @PostMapping
    @PreAuthorize("hasAuthority('dept:edit')")
    public Result<Long> create(@RequestBody SysDept dept) {
        return Result.ok(deptService.create(dept).getId());
    }

    @Operation(summary = "修改部门")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('dept:edit')")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysDept dept) {
        deptService.update(id, dept);
        return Result.ok();
    }

    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('dept:edit')")
    public Result<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return Result.ok();
    }
}
