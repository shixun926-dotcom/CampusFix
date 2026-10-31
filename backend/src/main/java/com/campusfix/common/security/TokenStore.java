package com.campusfix.common.security;

import java.util.Set;

/**
 * 令牌/计数存储抽象。
 *
 * <p>生产环境走 Redis（多实例共享、重启不丢登录态）；
 * 本地教学环境若未安装 Redis，自动降级为进程内存储，保证"一条命令跑通"。
 * 降级仅影响多实例与重启存活，不影响单机功能正确性。</p>
 */
public interface TokenStore {

    void set(String key, String value, long ttlSeconds);

    String get(String key);

    void delete(String key);

    /** 自增并设置过期（仅首次设置时生效）。 */
    long increment(String key, long ttlSeconds);

    void sAdd(String key, String member, long ttlSeconds);

    Set<String> sMembers(String key);

    void sRemove(String key, String member);
}
