package com.campusfix.message.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单维度聊天消息（基线 §11.1）。
 *
 * <p>会话以工单为单位：报修人 ↔ 当前维修人一对一；管理员/后勤作为相关方可查看
 * 也可发送。消息必须落库（离线可补），WebSocket 只负责实时送达。</p>
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long senderId;
    private Long receiverId;
    /** 1-500 字符，一期纯文本，禁止图片/表情包 */
    private String content;
    private Integer readFlag;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
