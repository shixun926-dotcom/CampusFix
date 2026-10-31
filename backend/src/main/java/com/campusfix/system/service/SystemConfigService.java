package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.system.entity.SysConfig;
import com.campusfix.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统参数读取。带本地缓存（60s），避免每次流转都查库。
 * 信誉分阈值、评价权重、自动流转天数等一律从这里取，不硬编码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final SysConfigMapper configMapper;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(String value, long expireAt) {
    }

    public String getString(String key, String defaultValue) {
        Cached c = cache.get(key);
        long now = System.currentTimeMillis();
        if (c != null && c.expireAt > now) {
            return c.value();
        }
        SysConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        String value = config == null ? defaultValue : config.getConfigValue();
        cache.put(key, new Cached(value, now + CACHE_TTL_MS));
        return value;
    }

    public int getInt(String key, int defaultValue) {
        String v = getString(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            log.warn("参数 {} 不是合法整数：{}，回退默认值 {}", key, v, defaultValue);
            return defaultValue;
        }
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        String v = getString(key, defaultValue.toPlainString());
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<SysConfig> listAll() {
        return configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getConfigKey));
    }

    public void update(String key, String value) {
        SysConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key));
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "参数不存在：" + key);
        }
        config.setConfigValue(value);
        configMapper.updateById(config);
        cache.remove(key);
    }

    public void evictAll() {
        cache.clear();
    }
}
