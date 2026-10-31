package com.campusfix.message.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.message.dto.MessageDtos.NotificationPageVO;
import com.campusfix.message.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知：列表、未读数、已读。任何登录用户可用，数据按 receiver_id 隔离。
 */
@Tag(name = "站内通知")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "通知列表（附未读数）")
    @GetMapping
    public Result<NotificationPageVO> page(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) Boolean unread) {
        return Result.ok(notificationService.page(CurrentUserHolder.id(), page, size, type, unread));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.unreadCount(CurrentUserHolder.id()));
    }

    @Operation(summary = "标记单条已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(CurrentUserHolder.id(), id);
        return Result.ok(null);
    }

    @Operation(summary = "全部已读")
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.ok(notificationService.markAllRead(CurrentUserHolder.id()));
    }
}
