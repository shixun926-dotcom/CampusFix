package com.campusfix.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务开关：自动派单（AutoAssignJob）、后续的自动验收与故障预警都挂在这里。
 * 单机部署，用 Spring 自带调度即可，不引入 XXL-Job。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
