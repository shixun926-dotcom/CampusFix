package com.campusfix.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置：campusfix.jwt.*
 */
@Data
@Component
@ConfigurationProperties(prefix = "campusfix.jwt")
public class JwtProperties {

    /** HS256 密钥，至少 32 字节 */
    private String secret;

    /** access token 有效期（秒），默认 2 小时 */
    private long accessTtlSeconds = 7200;

    /** refresh token 有效期（秒），默认 7 天 */
    private long refreshTtlSeconds = 604800;
}
