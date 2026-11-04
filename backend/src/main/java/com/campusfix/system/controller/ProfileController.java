package com.campusfix.system.controller;

import com.campusfix.common.result.Result;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.log.OperLog;
import com.campusfix.system.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人中心：维修工自助维护个人资料与头像。
 *
 * <p>仅维修人员可用（学生 / 管理员 / 后勤走各自入口，一期需求限定维修工）。
 * 头像为强制项，涉及学生安全追溯。
 */
@Tag(name = "个人中心")
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "我的个人资料")
    @GetMapping
    @PreAuthorize("hasAuthority('profile:view')")
    public Result<UserDtos.ProfileVO> profile() {
        return Result.ok(profileService.profile());
    }

    @Operation(summary = "保存个人资料（姓名 / 联系方式 / 头像）")
    @PutMapping
    @PreAuthorize("hasAuthority('profile:view')")
    @OperLog(module = "个人中心", action = "保存个人资料")
    public Result<UserDtos.ProfileVO> update(@Valid @RequestBody UserDtos.ProfileUpdateRequest req) {
        return Result.ok(profileService.update(req));
    }
}
