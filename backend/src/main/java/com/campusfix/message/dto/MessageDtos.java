package com.campusfix.message.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知与聊天的传输对象。
 */
public final class MessageDtos {

    private MessageDtos() {
    }

    public record NotificationVO(
            Long id,
            String type,
            String title,
            String content,
            Long bizId,
            Integer readFlag,
            LocalDateTime createdAt) {
    }

    /** 通知列表：附未读数，前端角标直接用，不必再发一次请求。 */
    public record NotificationPageVO(
            List<NotificationVO> list,
            long total,
            long unreadCount) {
    }

    public record ChatMessageVO(
            Long id,
            Long orderId,
            Long senderId,
            String senderName,
            Long receiverId,
            String content,
            Integer readFlag,
            LocalDateTime createdAt) {
    }

    public record ChatSendRequest(String content) {
    }
}
