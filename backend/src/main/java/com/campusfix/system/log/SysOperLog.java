package com.campusfix.system.log;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（sys_oper_log）。表没有 updated_at / deleted 列，不继承 BaseEntity。
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    /** 业务模块 */
    private String module;
    /** 操作描述 */
    private String operation;
    /** 类名.方法名 */
    private String method;
    private String requestUri;
    /** 请求参数（脱敏、截断 2000） */
    private String requestParam;
    private String ip;
    /** 1 成功 0 失败 */
    private Integer status;
    private String errorMsg;
    private Integer costMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
