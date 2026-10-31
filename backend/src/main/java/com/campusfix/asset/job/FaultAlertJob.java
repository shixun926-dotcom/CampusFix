package com.campusfix.asset.job;

import com.campusfix.asset.service.FaultAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 高频故障预警任务（基线 §12.3：每天 08:00）。
 *
 * <p>同一楼栋 + 最近 7 天 + 同一故障类型 + ≥5 单 → 写一条 fault_alert。
 * 单机部署下不存在多实例竞争；将来多实例需先加分布式锁再放开。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaultAlertJob {

    private final FaultAlertService faultAlertService;

    @Scheduled(cron = "${campusfix.stats.fault-alert-cron:0 0 8 * * ?}")
    public void run() {
        try {
            int inserted = faultAlertService.scan();
            if (inserted > 0) {
                log.info("[FaultAlertJob] 本次生成 {} 条高频故障预警", inserted);
            }
        } catch (Exception e) {
            // 定时任务不能抛出异常，否则后续调度会被取消
            log.error("[FaultAlertJob] 执行失败", e);
        }
    }
}
