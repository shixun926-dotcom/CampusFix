package com.campusfix.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单图片。数据库只存 objectKey，落地 URL 由 FileStorageService 生成，
 * 便于 storage.type 在 local / oss 之间切换。
 */
@Data
@TableName("repair_order_image")
public class RepairOrderImage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    /** REPORT 报修 / BEFORE 维修前 / AFTER 维修后 */
    private String stage;
    private String objectKey;
    private String fileName;
    private Integer fileSize;
    private Integer sort;
    private Long uploaderId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
