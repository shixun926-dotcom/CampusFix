package com.campusfix.common.security;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置：campusfix.jwt.*
 *
 * <p>密钥安全约束：HS256 要求密钥长度 ≥ 256 位（32 字节），否则 {@code Keys.hmacShaKeyFor}
 * 会抛 {@link java.security.InvalidKeyException}。为避免运行期才暴露，这里在启动时即校验：
 * 未配置（空）或长度不足 32 字节时直接拒绝启动，防止用弱密钥/默认密钥上线。</p>
 */
@Slf4j
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

    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT 密钥未配置：请通过环境变量 JWT_SECRET 注入（生成方式：openssl rand -base64 48）。"
                            + "出于安全考虑，项目不再提供默认密钥。");
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT 密钥过短（当前 " + secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            + " 字节）：HS256 要求至少 32 字节，请使用更强的随机密钥。");
        }
        log.info("JWT 密钥已加载（长度 {} 字节）", secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }
}
