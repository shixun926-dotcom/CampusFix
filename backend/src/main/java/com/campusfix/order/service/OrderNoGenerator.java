package com.campusfix.order.service;

import com.campusfix.common.security.TokenStore;
import com.campusfix.order.mapper.RepairOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 工单号生成：{@code yyyyMMdd + 3 位日序号}，如 {@code 20261030001}。
 *
 * <p>主路径是 {@link TokenStore} 自增（生产 Redis、本地降级进程内）。
 * 但进程内存储在重启后会归零，因此首先生成时把「当天库内已用的最大序号」记为基准，
 * 之后 {@code 基准 + 自增} 即为单号 —— 重启、换机器都不会与已有单号撞车。</p>
 *
 * <p>单号只是后台流转与追溯用，不对学生展示（基线 A16）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 自增键保留 2 天，跨天后自然过期 */
    private static final long TTL_SECONDS = 2 * 24 * 3600L;

    private final TokenStore tokenStore;
    private final RepairOrderMapper orderMapper;

    public String next() {
        String day = LocalDate.now().format(DAY);

        String baseKey = "order:base:" + day;
        String cached = tokenStore.get(baseKey);
        long base;
        if (cached == null) {
            base = orderMapper.selectMaxSeqOfDay(day);
            tokenStore.set(baseKey, String.valueOf(base), TTL_SECONDS);
            log.debug("工单号基准初始化 day={} base={}", day, base);
        } else {
            base = Long.parseLong(cached);
        }

        long seq = base + tokenStore.increment("order:seq:" + day, TTL_SECONDS);
        return day + String.format("%03d", seq);
    }
}
