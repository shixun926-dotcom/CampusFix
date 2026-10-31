package com.campusfix.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 工单规则配置：campusfix.order.*
 *
 * <p>这些数值写进了《项目注意文档》作为业务基线（24h 自动派单、3 天自动验收、3 天质保、9 张图），
 * 抽成配置是为了后期调参不必改代码重新打包。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "campusfix.order")
public class OrderProperties {

    /** 审核通过后超过该小时数仍无人接单，触发自动派单 */
    private int autoAssignHours = 24;

    /** 提交维修结果后超过该天数未确认，自动验收 */
    private int autoConfirmDays = 3;

    /** 单张工单最多图片数（报修 + 维修前 + 维修后累计） */
    private int maxImages = 9;

    /** 质保期天数：同位置同类型已完成单在该天数内再次报修，判定为质保返修 */
    private int warrantyDays = 3;

    /** 单张图片大小上限（MB） */
    private int maxImageMb = 10;

    /** 延期确认申请通过后，自动验收截止时间顺延的天数 */
    private int extensionDays = 3;

    /** 自动验收任务的 cron，默认每天 02:00 */
    private String autoConfirmCron = "0 0 2 * * ?";
}
