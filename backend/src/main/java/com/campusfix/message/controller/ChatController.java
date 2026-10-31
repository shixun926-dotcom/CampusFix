package com.campusfix.message.controller;

import com.campusfix.common.result.Result;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.message.dto.MessageDtos.ChatMessageVO;
import com.campusfix.message.dto.MessageDtos.ChatSendRequest;
import com.campusfix.message.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单维度聊天：相关方（报修人 / 当前维修人 / 后勤 / 管理员）可收发。
 */
@Tag(name = "工单聊天")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "会话历史（拉取即已读）")
    @GetMapping("/orders/{id}/messages")
    public Result<List<ChatMessageVO>> list(@PathVariable Long id) {
        return Result.ok(chatService.list(id, CurrentUserHolder.get()));
    }

    @Operation(summary = "发送消息（1-500 字）")
    @PostMapping("/orders/{id}/messages")
    public Result<ChatMessageVO> send(@PathVariable Long id, @Valid @RequestBody ChatSendRequest req) {
        return Result.ok(chatService.send(id, req, CurrentUserHolder.get()));
    }
}
