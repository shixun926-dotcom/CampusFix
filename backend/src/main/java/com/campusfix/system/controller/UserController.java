package com.campusfix.system.controller;

import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.system.dto.ImportDtos.ImportResult;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.excel.StudentExcelTemplate;
import com.campusfix.system.log.OperLog;
import com.campusfix.system.service.StudentImportService;
import com.campusfix.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StudentImportService studentImportService;
    private final StudentExcelTemplate studentExcelTemplate;

    @Operation(summary = "用户分页")
    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public Result<PageResult<UserDtos.UserVO>> page(UserDtos.Query query) {
        return Result.ok(userService.page(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:list')")
    public Result<UserDtos.UserVO> detail(@PathVariable Long id) {
        return Result.ok(userService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @PreAuthorize("hasAuthority('user:add')")
    @OperLog(module = "用户管理", action = "新增用户")
    public Result<Long> create(@Valid @RequestBody UserDtos.SaveRequest req) {
        return Result.ok(userService.create(req));
    }

    @Operation(summary = "手动添加单个学生")
    @PostMapping("/students")
    @PreAuthorize("hasAuthority('user:add')")
    @OperLog(module = "用户管理", action = "添加学生")
    public Result<Long> createStudent(@Valid @RequestBody UserDtos.StudentCreateRequest req) {
        return Result.ok(userService.createStudent(req));
    }

    @Operation(summary = "下载学生导入模板")
    @GetMapping("/import/template")
    @PreAuthorize("hasAuthority('user:import')")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] bytes = studentExcelTemplate.build();
        String filename = URLEncoder.encode("学生导入模板.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");

        response.reset();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }

    @Operation(summary = "批量导入学生（Excel）")
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('user:import')")
    @OperLog(module = "用户管理", action = "批量导入学生")
    public Result<ImportResult> importStudents(@RequestParam("file") MultipartFile file) {
        return Result.ok(studentImportService.importStudents(file));
    }

    @Operation(summary = "修改用户")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(module = "用户管理", action = "修改用户")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserDtos.SaveRequest req) {
        userService.update(id, req);
        return Result.ok();
    }

    @Operation(summary = "编辑用户基本信息（部分更新，动态 SQL）")
    @PutMapping("/{id}/basic")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(module = "用户管理", action = "编辑用户",
            masked = {"password", "phone", "oldPassword", "newPassword"})
    public Result<UserDtos.UserVO> updateBasic(@PathVariable Long id,
                                               @Valid @RequestBody UserDtos.BasicUpdateRequest req) {
        return Result.ok(userService.updateBasic(id, req));
    }

    @Operation(summary = "启用/禁用")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(module = "用户管理", action = "启用/禁用")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(module = "用户管理", action = "重置密码")
    public Result<Map<String, String>> resetPassword(@PathVariable Long id) {
        return Result.ok(Map.of("password", userService.resetPassword(id)));
    }

    @Operation(summary = "调整信誉分")
    @PostMapping("/{id}/credit")
    @PreAuthorize("hasAuthority('user:edit')")
    @OperLog(module = "用户管理", action = "调整信誉分")
    public Result<Integer> adjustCredit(@PathVariable Long id,
                                        @RequestBody UserDtos.CreditAdjustRequest req) {
        return Result.ok(userService.adjustCredit(id, req.delta(), req.reason(), null, CurrentUserHolder.id()));
    }

    @Operation(summary = "删除成员（逻辑删除）")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    @OperLog(module = "用户管理", action = "删除成员")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "批量删除成员（逐条校验，返回失败明细）")
    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('user:delete')")
    @OperLog(module = "用户管理", action = "批量删除成员")
    public Result<UserDtos.DeleteResult> batchDelete(@Valid @RequestBody UserDtos.DeleteBatchRequest req) {
        return Result.ok(userService.deleteBatch(req.getIds()));
    }
}
