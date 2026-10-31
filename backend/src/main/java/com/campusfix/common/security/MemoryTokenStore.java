package com.campusfix.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 进程内令牌存储（本地无 Redis 时的降级实现）。
 * 仅用于单机开发/演示：重启后登录态失效，也不支持多实例。
 */
@Slf4j
@Component
public class MemoryTokenStore implements TokenStore {

    private record Holder(String value, long expireAt) {
        boolean expired() {
            return expireAt > 0 && System.currentTimeMillis() > expireAt;
        }
    }

    private final Map<String, Holder> values = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sets = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public void set(String key, String value, long ttlSeconds) {
        values.put(key, new Holder(value, expireAt(ttlSeconds)));
    }

    @Override
    public String get(String key) {
        Holder h = values.get(key);
        if (h == null) {
            return null;
        }
        if (h.expired()) {
            values.remove(key);
            return null;
        }
        return h.value();
    }

    @Override
    public void delete(String key) {
        values.remove(key);
        sets.remove(key);
        counters.remove(key);
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        Holder h = values.get(key);
        if (h != null && h.expired()) {
            values.remove(key);
            counters.remove(key);
        }
        return counters.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    @Override
    public void sAdd(String key, String member, long ttlSeconds) {
        sets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(member);
    }

    @Override
    public Set<String> sMembers(String key) {
        return sets.getOrDefault(key, Set.of());
    }

    @Override
    public void sRemove(String key, String member) {
        Set<String> set = sets.get(key);
        if (set != null) {
            set.remove(member);
        }
    }

    private long expireAt(long ttlSeconds) {
        return ttlSeconds > 0 ? System.currentTimeMillis() + ttlSeconds * 1000 : 0;
    }
}
