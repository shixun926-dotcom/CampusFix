package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.common.config.OrderProperties;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.message.service.NotificationService;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.dto.ScheduleDtos;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairOrderImage;
import com.campusfix.order.entity.RepairRecord;
import com.campusfix.order.mapper.RepairOrderImageMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.mapper.RepairRecordMapper;
import com.campusfix.order.statemachine.OrderStateMachine;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 维修过程：到达打点 → 开始打点 → 录入维修内容 → 提交结果。
 *
 * <p>打点只更新字段、不改变工单状态；真正推进状态的是 {@code SUBMIT_RESULT}
 * （处理中/挂起 → 待确认），走状态机。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private static final String STAGE_BEFORE = "BEFORE";
    private static final String STAGE_AFTER = "AFTER";

    private final RepairRecordMapper recordMapper;
    private final RepairOrderMapper orderMapper;
    private final RepairOrderImageMapper imageMapper;
    private final SysUserMapper userMapper;
    private final OrderStateMachine stateMachine;
    private final OrderQueryService queryService;
    private final OrderProperties orderProperties;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 查询

    public ScheduleDtos.RecordVO get(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!queryService.canView(order, me)) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权查看该工单的维修记录");
        }
        RepairRecord record = findRecord(orderId);
        return record == null ? null : toVO(record);
    }

    // ------------------------------------------------------------------ 打点

    /** 到达报修点。 */
    @Transactional
    public ScheduleDtos.RecordVO arrive(Long orderId, LoginUser me) {
        RepairOrder order = requireProcessing(orderId, me);
        RepairRecord record = getOrCreate(order);
        if (record.getArriveAt() != null) {
            throw new BizException(ResultCode.PARAM_INVALID, "已记录到达时间，无需重复打点");
        }
        LocalDateTime now = LocalDateTime.now();
        record.setArriveAt(now);
        recordMapper.updateById(record);
        orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .set(RepairOrder::getArrivedAt, now));
        log.info("到达打点 orderId={} repairer={}", orderId, me.getId());
        // 打点不经过状态机，通知直接发（基线 §11.4：维修开始/已到达 → 学生）
        notificationService.notify(order.getReporterId(), NotificationService.TYPE_ORDER,
                "维修人员已到达", "工单「" + order.getTitle() + "」的维修人员已到达现场。", orderId);
        return toVO(recordMapper.selectById(record.getId()));
    }

    /** 开始维修。必须先记录到达，保证「响应时长」可统计。 */
    @Transactional
    public ScheduleDtos.RecordVO start(Long orderId, LoginUser me) {
        RepairOrder order = requireProcessing(orderId, me);
        RepairRecord record = getOrCreate(order);
        if (record.getArriveAt() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "请先记录到达时间");
        }
        if (record.getStartAt() != null) {
            throw new BizException(ResultCode.PARAM_INVALID, "已记录开始时间，无需重复打点");
        }
        LocalDateTime now = LocalDateTime.now();
        record.setStartAt(now);
        recordMapper.updateById(record);
        orderMapper.update(null, new LambdaUpdateWrapper<RepairOrder>()
                .eq(RepairOrder::getId, orderId)
                .set(RepairOrder::getStartedAt, now));
        log.info("开始维修打点 orderId={} repairer={}", orderId, me.getId());
        notificationService.notify(order.getReporterId(), NotificationService.TYPE_ORDER,
                "开始维修", "工单「" + order.getTitle() + "」已开始维修。", orderId);
        return toVO(recordMapper.selectById(record.getId()));
    }

    // ------------------------------------------------------------------ 记录

    /** 保存维修内容与维修前照片（可多次调用，覆盖式保存）。 */
    @Transactional
    public ScheduleDtos.RecordVO save(Long orderId, ScheduleDtos.RecordRequest req, LoginUser me) {
        RepairOrder order = requireProcessing(orderId, me);
        RepairRecord record = getOrCreate(order);
        if (StringUtils.hasText(req.content())) {
            record.setContent(req.content().trim());
        }
        record.setMaterial(trimToNull(req.material()));
        record.setResult(trimToNull(req.result()));
        record.setRemark(trimToNull(req.remark()));
        recordMapper.updateById(record);

        appendImages(orderId, req.images(), STAGE_BEFORE, me.getId());
        return toVO(recordMapper.selectById(record.getId()));
    }

    /**
     * 提交维修结果：落记录 + 维修后照片，随后把工单推到「待确认」。
     *
     * <p>{@code finished_at} 与 {@code end_at} 都取同一时刻，避免统计口径出现秒级偏差。</p>
     */
    @Transactional
    public ScheduleDtos.RecordVO complete(Long orderId, ScheduleDtos.CompleteRequest req, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有当前维修人可以提交结果");
        }
        if (!order.isStatus(OrderStatus.PROCESSING) && !order.isStatus(OrderStatus.SUSPENDED)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "当前状态「" + order.statusEnum().getLabel() + "」不能提交维修结果");
        }

        RepairRecord record = getOrCreate(order);
        if (record.getStartAt() == null) {
            // 现场直接干完没点「开始维修」的情况，把开始时间落成到达时间，避免时长为空
            record.setStartAt(record.getArriveAt() == null ? LocalDateTime.now() : record.getArriveAt());
        }
        LocalDateTime now = LocalDateTime.now();
        record.setEndAt(now);
        record.setContent(req.content().trim());
        record.setMaterial(trimToNull(req.material()));
        record.setResult(req.result().trim());
        record.setRemark(trimToNull(req.remark()));
        recordMapper.updateById(record);

        appendImages(orderId, req.images(), STAGE_AFTER, me.getId());

        stateMachine.fire(orderId, OrderAction.SUBMIT_RESULT, me,
                "维修完成，等待报修人确认",
                Map.of("confirmDeadline", now.plusDays(orderProperties.getAutoConfirmDays())));
        log.info("提交维修结果 orderId={} repairer={} 耗时={}分钟", orderId, me.getId(),
                Duration.between(record.getStartAt(), now).toMinutes());
        return toVO(recordMapper.selectById(record.getId()));
    }

    // ------------------------------------------------------------------ 挂起 / 恢复 / 无法维修

    /** 挂起（必填原因，如缺配件、报修人不在）。挂起时长不计入维修时长。 */
    @Transactional
    public ScheduleDtos.RecordVO suspend(Long orderId, String reason, LoginUser me) {
        RepairOrder order = requireProcessing(orderId, me);
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.PARAM_INVALID, "请填写挂起原因");
        }
        stateMachine.fire(orderId, OrderAction.SUSPEND, me, reason.trim(), Map.of());
        log.info("挂起工单 orderId={} repairer={} reason={}", orderId, me.getId(), reason);
        return recordOrNull(orderId);
    }

    /** 恢复（挂起 → 处理中），自动累计挂起时长。 */
    @Transactional
    public ScheduleDtos.RecordVO resume(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有当前维修人可以恢复工单");
        }
        if (!order.isStatus(OrderStatus.SUSPENDED)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "只有「挂起」状态的工单可以恢复");
        }
        stateMachine.fire(orderId, OrderAction.RESUME, me, "恢复维修", Map.of());
        log.info("恢复工单 orderId={} repairer={}", orderId, me.getId());
        return recordOrNull(orderId);
    }

    /** 无法维修（必填原因），工单转已驳回并通知报修人，可转投诉。 */
    @Transactional
    public ScheduleDtos.RecordVO unrepairable(Long orderId, String reason, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有当前维修人可以判定无法维修");
        }
        if (!order.isStatus(OrderStatus.PROCESSING) && !order.isStatus(OrderStatus.SUSPENDED)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "只有「处理中 / 挂起」状态的工单可以判定无法维修");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.PARAM_INVALID, "请填写无法维修的原因");
        }
        stateMachine.fire(orderId, OrderAction.UNREPAIRABLE, me, reason.trim(), Map.of());
        log.info("判定无法维修 orderId={} repairer={} reason={}", orderId, me.getId(), reason);
        return recordOrNull(orderId);
    }

    /** 挂起/恢复/无法维修可能发生在打点之前，记录行不存在时返回 null */
    private ScheduleDtos.RecordVO recordOrNull(Long orderId) {
        RepairRecord r = findRecord(orderId);
        return r == null ? null : toVO(r);
    }

    // ------------------------------------------------------------------ 内部

    private RepairOrder requireProcessing(Long orderId, LoginUser me) {
        RepairOrder order = queryService.getOrder(orderId);
        if (!me.getId().equals(order.getAssigneeId())) {
            throw new BizException(ResultCode.FORBIDDEN, "只有当前维修人可以操作维修过程");
        }
        if (!order.isStatus(OrderStatus.PROCESSING)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "工单当前「" + order.statusEnum().getLabel() + "」，请先确认上门时间进入处理中");
        }
        return order;
    }

    private RepairRecord findRecord(Long orderId) {
        return recordMapper.selectOne(new LambdaQueryWrapper<RepairRecord>()
                .eq(RepairRecord::getOrderId, orderId).last("LIMIT 1"));
    }

    private RepairRecord getOrCreate(RepairOrder order) {
        RepairRecord record = findRecord(order.getId());
        if (record != null) {
            return record;
        }
        record = new RepairRecord();
        record.setOrderId(order.getId());
        record.setRepairerId(order.getAssigneeId());
        recordMapper.insert(record);
        return recordMapper.selectById(record.getId());
    }

    /** 追加工单图片，整单累计不超过上限（报修 + 维修前 + 维修后）。 */
    private void appendImages(Long orderId, List<OrderDtos.ImageItem> images, String defaultStage, Long uploaderId) {
        if (images == null || images.isEmpty()) {
            return;
        }
        Long existing = imageMapper.selectCount(new LambdaQueryWrapper<RepairOrderImage>()
                .eq(RepairOrderImage::getOrderId, orderId));
        long total = (existing == null ? 0 : existing) + images.size();
        int max = orderProperties.getMaxImages();
        if (total > max) {
            throw new BizException(ResultCode.IMAGE_LIMIT,
                    "整单图片最多 " + max + " 张，当前已有 " + (existing == null ? 0 : existing) + " 张");
        }
        // 追加时接在已有排序之后
        int sort = existing == null ? 0 : existing.intValue();
        for (OrderDtos.ImageItem img : images) {
            RepairOrderImage image = new RepairOrderImage();
            image.setOrderId(orderId);
            image.setStage(StringUtils.hasText(img.stage()) ? img.stage().toUpperCase() : defaultStage);
            image.setObjectKey(img.objectKey().trim());
            image.setFileName(img.fileName());
            image.setFileSize(img.fileSize());
            image.setSort(img.sort() == null ? sort : img.sort());
            image.setUploaderId(uploaderId);
            imageMapper.insert(image);
            sort++;
        }
    }

    private ScheduleDtos.RecordVO toVO(RepairRecord r) {
        ScheduleDtos.RecordVO vo = new ScheduleDtos.RecordVO();
        vo.setId(r.getId());
        vo.setOrderId(r.getOrderId());
        vo.setRepairerId(r.getRepairerId());
        if (r.getRepairerId() != null) {
            SysUser u = userMapper.selectById(r.getRepairerId());
            vo.setRepairerName(u == null ? null : u.getRealName());
        }
        vo.setArriveAt(r.getArriveAt());
        vo.setStartAt(r.getStartAt());
        vo.setEndAt(r.getEndAt());
        vo.setContent(r.getContent());
        vo.setMaterial(r.getMaterial());
        vo.setResult(r.getResult());
        vo.setRemark(r.getRemark());
        if (r.getStartAt() != null && r.getEndAt() != null) {
            vo.setDurationMinutes(Duration.between(r.getStartAt(), r.getEndAt()).toMinutes());
        }
        return vo;
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
