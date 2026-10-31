package com.campusfix.system.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标在写操作接口方法上，由 {@link OperLogAspect} 记录到 sys_oper_log。
 *
 * <pre>
 * &#64;OperLog(module = "工单", action = "审核通过")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 业务模块，如「工单」「用户管理」 */
    String module();

    /** 操作描述，如「审核通过」「重置密码」 */
    String action();

    /** 参数中需要脱敏的字段名（JSON 序列化后按 key 替换为 ***），默认密码类 */
    String[] masked() default {"password", "oldPassword", "newPassword"};
}
