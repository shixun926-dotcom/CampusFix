package com.campusfix.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨域配置：campusfix.cors.*
 *
 * <p>前后端分离后前端可能部署在任意域，白名单必须可配而不是写死。
 * 默认只放行本机开发端口，生产通过 {@code CORS_ALLOWED_ORIGINS} 注入实际域名。</p>
 *
 * <p>注意：开启 {@code allowCredentials} 时不能使用裸通配 {@code *}，
 * 但可以用 {@code http://localhost:*} 这类带端口的模式（Spring 的 originPatterns 支持）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "campusfix.cors")
public class CorsProperties {

    /** 允许的前端源，支持通配，多个用逗号分隔。 */
    private List<String> allowedOrigins = new ArrayList<>(
            List.of("http://localhost:*", "http://127.0.0.1:*", "http://localhost:5173"));

    private boolean allowCredentials = true;

    private long maxAge = 3600L;
}
