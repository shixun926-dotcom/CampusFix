package com.campusfix.system.controller;

import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.log.OperLog;
import com.campusfix.system.service.ProfileChangeService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信息更改申请：维修工提交 → 后勤管理员审核。
 */
@Tag(name = "信息更改申请")
@RestController
@RequestMapping("/api/v1/profile-changes")
@RequiredArgsConstructor
public class ProfileChangeController {

    private final ProfileChangeService profileChangeService;

    @Operation(summary = "提交信息更改申请")
    @PostMapping
    @PreAuthorize("hasAuthority('profile:view')")
    @OperLog(module = "个人中心", action = "提交信息更改申请")
    public Result<UserDtos.ProfileChangeVO> submit(@Valid @RequestBody UserDtos.ProfileChangeSubmitRequest req) {
        return Result.ok(profileChangeService.submit(req));
    }

    @Operation(summary = "我的申请记录")
    @GetMapping("/mine")
    @PreAuthorize("hasAuthority('profile:view')")
    public Result<PageResult<UserDtos.ProfileChangeVO>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(profileChangeService.myRequests(page, size));
    }

    @Operation(summary = "审核列表（后勤管理员 / 系统管理员）")
    @GetMapping("/review")
    @PreAuthorize("hasAnyAuthority('profile:change:review', 'profile:view')")
    public Result<PageResult<UserDtos.ProfileChangeVO>> reviewList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(profileChangeService.reviewList(status, page, size, CurrentUserHolder.get()));
    }

    @Operation(summary = "审核（通过 / 打回）")
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('profile:change:review')")
    @OperLog(module = "个人中心", action = "审核信息更改申请")
    public Result<UserDtos.ProfileChangeVO> review(@PathVariable Long id,
                                                   @Valid @RequestBody UserDtos.ProfileChangeReviewRequest req) {
        return Result.ok(profileChangeService.review(id, req, CurrentUserHolder.get()));
    }
}
