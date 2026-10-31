package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 维修过程记录，一单一条（{@code uk_order}）。
 *
 * <p>三个阶段时间点分别对应：到达报修点、开始维修、维修结束。
 * 「平均维修时长」按 {@code start_at → end_at} 统计，「平均处理时长」按接单到完成统计。</p>
 */
@Data
@TableName("repair_record")
public class RepairRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long repairerId;
    private LocalDateTime arriveAt;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** 维修内容 */
    private String content;
    /** 使用材料 */
    private String material;
    /** 维修结果 */
    private String result;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
