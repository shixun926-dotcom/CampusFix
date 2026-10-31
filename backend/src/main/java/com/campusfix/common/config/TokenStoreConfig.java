package com.campusfix.common.config;

import com.campusfix.common.security.MemoryTokenStore;
import com.campusfix.common.security.RedisTokenStore;
import com.campusfix.common.security.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 令牌存储选择器。
 *
 * <p>campusfix.cache.type = redis（生产）/ memory（无 Redis 的本地演示）/ auto（默认，探测后决定）。
 * auto 模式下 Redis 不可达时降级为进程内存储，并在启动日志 WARN 提示。</p>
 */
@Slf4j
@Configuration
public class TokenStoreConfig {

    @Bean
    @Primary
    public TokenStore tokenStore(RedisTokenStore redisStore,
                                 MemoryTokenStore memoryStore,
                                 @Value("${campusfix.cache.type:auto}") String type) {

        if ("memory".equalsIgnoreCase(type)) {
            log.warn("令牌存储：进程内模式（campusfix.cache.type=memory）。重启后登录态失效，仅适用于本地开发。");
            return memoryStore;
        }
        if ("redis".equalsIgnoreCase(type)) {
            log.info("令牌存储：Redis 模式（campusfix.cache.type=redis）。");
            return redisStore;
        }
        try {
            if (redisStore.ping()) {
                log.info("令牌存储：Redis 可用，使用 Redis 模式。");
                return redisStore;
            }
            log.warn("Redis 未响应 PING，降级为进程内令牌存储。生产环境请启动 Redis 并设置 campusfix.cache.type=redis。");
        } catch (Exception e) {
            log.warn("Redis 连接失败（{}），降级为进程内令牌存储。仅适用于本地开发/演示。", e.getMessage());
        }
        return memoryStore;
    }
}
