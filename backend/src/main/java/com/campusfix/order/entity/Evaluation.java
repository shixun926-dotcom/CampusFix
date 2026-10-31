package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单评价，一单一条（{@code uk_order}）。
 *
 * <p>{@code totalScore} 是三维加权结果，落库时算好，避免每次统计重新乘权重 ——
 * 权重后续可能被管理员改，历史分数不应随之变动。</p>
 */
@Data
@TableName("evaluation")
public class Evaluation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long studentId;
    private Long repairerId;
    /** 维修速度 1-5 */
    private Integer speedScore;
    /** 服务态度 1-5 */
    private Integer attitudeScore;
    /** 维修效果 1-5 */
    private Integer effectScore;
    /** 加权总分 1.00-5.00 */
    private BigDecimal totalScore;
    private String content;
    /** 1 匿名（对外隐藏报修人） */
    private Integer anonymous;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
