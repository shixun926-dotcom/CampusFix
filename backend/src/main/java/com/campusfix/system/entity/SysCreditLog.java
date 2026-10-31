package com.campusfix.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_credit_log")
public class SysCreditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer delta;
    private Integer beforeScore;
    private Integer afterScore;
    private String reason;
    private Long bizId;
    private Long operatorId;
    private LocalDateTime createdAt;
}
