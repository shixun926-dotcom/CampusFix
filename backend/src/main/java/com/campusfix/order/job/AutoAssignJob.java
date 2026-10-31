package com.campusfix.order.job;

import com.campusfix.order.service.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时自动派单任务。
 *
 * <p>每 10 分钟扫描一次「待处理超过 N 小时且无人接单」的工单，按在办最少原则派给维修工。
 * 单机部署下无需分布式锁；若将来多实例，用 Redis 抢占 {@code job:auto-assign} 即可，
 * 因为 {@link DispatchService#autoAssign()} 本身是幂等的（乐观锁会挡住重复派单）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoAssignJob {

    private final DispatchService dispatchService;

    @Scheduled(cron = "${campusfix.order.auto-assign-cron:0 */10 * * * ?}")
    public void run() {
        try {
            int assigned = dispatchService.autoAssign();
            if (assigned > 0) {
                log.info("[AutoAssignJob] 自动派单完成，本次派出 {} 张", assigned);
            }
        } catch (Exception e) {
            // 定时任务不能把异常抛出去，否则后续调度被取消
            log.error("[AutoAssignJob] 执行失败", e);
        }
    }
}
