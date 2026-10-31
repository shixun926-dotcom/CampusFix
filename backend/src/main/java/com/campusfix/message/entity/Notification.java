package com.campusfix.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。所有业务事件（工单流转、聊天、系统）落库的唯一载体，
 * 未读数与通知列表都查这张表；WebSocket 只做实时推送，不做持久化。
 */
@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiverId;
    /** ORDER_STATUS / CHAT / COMPLAINT / SYSTEM */
    private String type;
    private String title;
    private String content;
    /** 关联业务 ID，通常为工单 ID，前端据此跳转 */
    private Long bizId;
    private Integer readFlag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
