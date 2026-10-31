package com.campusfix.order.job;

import com.campusfix.order.service.AcceptanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时自动验收任务（基线 §5.5：每日 02:00）。
 *
 * <p>扫描 {@code status='PENDING_CONFIRM'} 且 {@code confirm_deadline} 已过的工单，
 * 置为已完成、标记 {@code auto_confirmed=1}，并扣报修人信誉分。</p>
 *
 * <p>按 deadline 判断而不是按「最后更新时间 + 3 天」，是为了让「延期审批」能直接顺延
 * 截止时间，不必引入额外的暂停状态。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoConfirmJob {

    private final AcceptanceService acceptanceService;

    @Scheduled(cron = "${campusfix.order.auto-confirm-cron:0 0 2 * * ?}")
    public void run() {
        try {
            int done = acceptanceService.autoConfirm();
            if (done > 0) {
                log.info("[AutoConfirmJob] 自动验收完成，本次处理 {} 张", done);
            }
        } catch (Exception e) {
            // 定时任务不能抛出异常，否则后续调度会被取消
            log.error("[AutoConfirmJob] 执行失败", e);
        }
    }
}
