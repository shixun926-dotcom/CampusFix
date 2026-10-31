package com.campusfix.system.controller;

import com.campusfix.common.result.Result;
import com.campusfix.system.entity.SysPermission;
import com.campusfix.system.entity.SysRole;
import com.campusfix.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色与权限")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @Operation(summary = "角色列表")
    @GetMapping("/roles")
    public Result<List<SysRole>> list() {
        return Result.ok(roleService.listAll());
    }

    @Operation(summary = "权限树")
    @GetMapping("/permissions/tree")
    public Result<List<SysPermission>> permissionTree() {
        return Result.ok(roleService.permissionTree());
    }

    @Operation(summary = "角色已分配权限")
    @GetMapping("/roles/{id}/permissions")
    public Result<List<Long>> permissions(@PathVariable Long id) {
        return Result.ok(roleService.permissionIdsOf(id));
    }

    @Operation(summary = "分配权限")
    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('role:auth')")
    public Result<Void> assign(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return Result.ok();
    }
}
