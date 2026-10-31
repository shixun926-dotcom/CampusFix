package com.campusfix.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 信誉分配置：campusfix.credit.*
 *
 * <p>信誉分只有一个修改入口 {@code UserService.adjustCredit}，每次变更写 sys_credit_log。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "campusfix.credit")
public class CreditProperties {

    /** 新账号初始分 */
    private int initScore = 100;

    /** 单次扣分幅度 */
    private int deductScore = 10;

    /** 低于该分值限制抢单接单 */
    private int limitThreshold = 60;
}
