package com.campusfix.common.security;

import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 签发、校验与白名单。
 *
 * <p>纯 JWT 无法"立即踢人/权限即时生效"，因此 access token 的 jti 与用户快照写入
 * {@link TokenStore}（生产为 Redis），注销时删除；角色/权限变更时清空该用户全部 jti。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String KEY_TOKEN = "campusfix:auth:token:";
    private static final String KEY_REFRESH = "campusfix:auth:refresh:";
    private static final String KEY_USER_TOKENS = "campusfix:auth:utokens:";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 access token，并把用户快照写入存储。 */
    public String createAccessToken(LoginUser user) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date exp = new Date(now.getTime() + properties.getAccessTtlSeconds() * 1000);

        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .id(jti)
                .claim("type", TYPE_ACCESS)
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();

        user.setJti(jti);
        try {
            tokenStore.set(KEY_TOKEN + jti, objectMapper.writeValueAsString(user),
                    properties.getAccessTtlSeconds());
            tokenStore.sAdd(KEY_USER_TOKENS + user.getId(), jti, properties.getAccessTtlSeconds() + 60);
        } catch (Exception e) {
            log.error("写入登录快照失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR);
        }
        return token;
    }

    /** 签发 refresh token。 */
    public String createRefreshToken(Long userId) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Date now = new Date();
        Date exp = new Date(now.getTime() + properties.getRefreshTtlSeconds() * 1000);

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .id(jti)
                .claim("type", TYPE_REFRESH)
                .issuedAt(now)
                .expiration(exp)
                .signWith(key())
                .compact();

        tokenStore.set(KEY_REFRESH + jti, String.valueOf(userId), properties.getRefreshTtlSeconds());
        return token;
    }

    /** 解析并返回 claims；过期抛 TOKEN_EXPIRED。 */
    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    /** 从 access token 载入登录用户；白名单不存在视为已失效。 */
    public LoginUser loadUser(String token) {
        Claims claims = parse(token);
        if (!TYPE_ACCESS.equals(claims.get("type", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String jti = claims.getId();
        String json = tokenStore.get(KEY_TOKEN + jti);
        if (json == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        try {
            return objectMapper.readValue(json, LoginUser.class);
        } catch (Exception e) {
            log.warn("解析登录快照失败 jti={}", jti, e);
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
    }

    /** 用 refresh token 换取新的 access token 所需的用户 ID。 */
    public Long parseRefresh(String refreshToken) {
        Claims claims = parse(refreshToken);
        if (!TYPE_REFRESH.equals(claims.get("type", String.class))) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String userId = tokenStore.get(KEY_REFRESH + claims.getId());
        if (userId == null) {
            throw new BizException(ResultCode.TOKEN_EXPIRED);
        }
        return Long.valueOf(userId);
    }

    /** 注销当前 token。 */
    public void logout(String jti, Long userId) {
        if (jti == null) {
            return;
        }
        tokenStore.delete(KEY_TOKEN + jti);
        tokenStore.sRemove(KEY_USER_TOKENS + userId, jti);
    }

    /** 清空该用户全部令牌（权限变更、禁用账号、改密时调用）。 */
    public void logoutAll(Long userId) {
        Set<String> jtis = tokenStore.sMembers(KEY_USER_TOKENS + userId);
        jtis.forEach(jti -> tokenStore.delete(KEY_TOKEN + jti));
        tokenStore.delete(KEY_USER_TOKENS + userId);
    }
}
