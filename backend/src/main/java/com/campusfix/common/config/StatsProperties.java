package com.campusfix.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统计与预警配置：campusfix.stats.*
 *
 * <p>重复故障预警口径（基线 §12.3）：同一楼栋 + 最近 N 天 + 同一故障类型 +
 * 工单数 ≥ 阈值 → 写一条 fault_alert。阈值与窗口都可运行期调整。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "campusfix.stats")
public class StatsProperties {

    /** 重复故障预警的回溯窗口（天） */
    private int faultAlertDays = 7;

    /** 同楼栋同类型工单达到该数量即预警 */
    private int faultAlertThreshold = 5;

    /** 预警扫描任务 cron，默认每天 08:00 */
    private String faultAlertCron = "0 0 8 * * ?";
}
