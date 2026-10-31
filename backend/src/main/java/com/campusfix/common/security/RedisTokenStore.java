package com.campusfix.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;

/**
 * Redis 令牌存储（生产实现）。
 */
@Component
@RequiredArgsConstructor
public class RedisTokenStore implements TokenStore {

    private final StringRedisTemplate redis;

    @Override
    public void set(String key, String value, long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        Long v = redis.opsForValue().increment(key);
        if (v != null && v == 1L) {
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        }
        return v == null ? 0L : v;
    }

    @Override
    public void sAdd(String key, String member, long ttlSeconds) {
        redis.opsForSet().add(key, member);
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public Set<String> sMembers(String key) {
        Set<String> members = redis.opsForSet().members(key);
        return members == null ? Collections.emptySet() : members;
    }

    @Override
    public void sRemove(String key, String member) {
        redis.opsForSet().remove(key, member);
    }

    /** 连通性探测，用于启动时决定是否降级。 */
    public boolean ping() {
        String pong = redis.execute(connection -> connection.ping(), true);
        return "PONG".equalsIgnoreCase(pong);
    }
}
