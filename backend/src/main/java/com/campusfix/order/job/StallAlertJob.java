package com.campusfix.order.job;

import com.campusfix.order.service.StallAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 状态滞留巡检任务（《系统设计说明书》§9.2 风险 R1 应对：默认每天 09:00）。
 *
 * <p>扫描「最后更新时间超过阈值仍停留在非终态」的工单，把清单推送给管理员。
 * 单机部署下无需分布式锁；将来多实例时，因 {@code order_stall_alert} 唯一键兜底，
 * 即使并发执行也只会成功推送一次（其余被唯一键拦下）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StallAlertJob {

    private final StallAlertService stallAlertService;

    @Scheduled(cron = "${campusfix.order.stall-alert-cron:0 0 9 * * ?}")
    public void run() {
        try {
            int pushed = stallAlertService.scan();
            if (pushed > 0) {
                log.info("[StallAlertJob] 滞留巡检完成，本次推送 {} 张滞留工单", pushed);
            }
        } catch (Exception e) {
            // 定时任务不能把异常抛出去，否则后续调度被取消；
            // 单次失败不影响下次调度，滞留工单会在下个周期被再次扫描推送。
            log.error("[StallAlertJob] 执行失败", e);
        }
    }
}
