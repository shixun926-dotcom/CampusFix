package com.campusfix.message.ws;

import com.campusfix.common.security.JwtTokenProvider;
import com.campusfix.common.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 配置：注册 {@code /ws} 端点，握手时从 query 参数校验 access token。
 *
 * <p>浏览器 WebSocket API 无法自定义 Authorization 头，因此约定
 * {@code ws(s)://host/ws?token=<accessToken>}；token 失效直接拒绝握手。
 * SecurityConfig 已对 {@code /ws/**} permitAll，真正的鉴权在拦截器这里。</p>
 */
@Slf4j
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PushWebSocketHandler pushHandler;
    private final JwtTokenProvider tokenProvider;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(pushHandler, "/ws")
                .addInterceptors(new AuthHandshakeInterceptor())
                // 握手请求来自浏览器 Origin（开发期是 5173），端点自身已有 token 鉴权
                .setAllowedOriginPatterns("*");
    }

    private class AuthHandshakeInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                       @NonNull ServerHttpResponse response,
                                       @NonNull WebSocketHandler wsHandler,
                                       @NonNull Map<String, Object> attributes) {
            if (request instanceof ServletServerHttpRequest servlet) {
                String token = servlet.getServletRequest().getParameter("token");
                if (token != null && !token.isBlank()) {
                    try {
                        LoginUser user = tokenProvider.loadUser(token);
                        attributes.put(PushWebSocketHandler.ATTR_USER, user);
                        return true;
                    } catch (Exception e) {
                        log.debug("WebSocket 握手 token 无效：{}", e.getMessage());
                    }
                }
            }
            return false;
        }

        @Override
        public void afterHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   Exception exception) {
            // 无需处理
        }
    }
}
