package com.campusfix.message.ws;

import com.campusfix.common.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;

/**
 * 站内消息推送处理器（原生 WebSocket 协议，非 STOMP）。
 *
 * <p>服务端只下行不承担业务上行：客户端发来的任何文本仅作心跳，回 PONG 保活。
 * 鉴权在握手拦截器完成（{@code /ws?token=accessToken}），进入本类的会话必然有效。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USER = "wsUser";

    private final WebSocketSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userIdOf(session);
        if (userId != null) {
            registry.register(userId, session);
            log.debug("WebSocket 已连接 userId={} sessionId={}", userId, session.getId());
            send(session, "{\"type\":\"CONNECTED\",\"payload\":{\"userId\":" + userId + "}}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userIdOf(session);
        if (userId != null) {
            registry.remove(userId, session);
            log.debug("WebSocket 已断开 userId={} status={}", userId, status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 心跳保活：任何上行文本都回 PONG，不做业务处理
        try {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        } catch (IOException ignored) {
            // 连接已坏，由 onClose 清理
        }
    }

    private Long userIdOf(WebSocketSession session) {
        Object user = session.getAttributes().get(ATTR_USER);
        return user instanceof LoginUser u ? u.getId() : null;
    }

    private void send(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException | IllegalStateException ignored) {
            // 初次欢迎消息发送失败不影响注册
        }
    }

    /** 供握手拦截器取用户属性时的类型安全读取。 */
    public static Map<String, Object> attrs(WebSocketSession session) {
        return session.getAttributes();
    }
}
