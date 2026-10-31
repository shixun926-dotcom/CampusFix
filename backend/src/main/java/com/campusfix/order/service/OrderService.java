package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.asset.entity.Location;
import com.campusfix.asset.service.LocationService;
import com.campusfix.common.config.OrderProperties;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.entity.RepairCategory;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.entity.RepairOrderImage;
import com.campusfix.order.mapper.RepairCategoryMapper;
import com.campusfix.order.mapper.RepairOrderImageMapper;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.statemachine.OrderStateMachine;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工单写操作：提交报修、审核、取消。
 *
 * <p>所有状态变更一律委托 {@link OrderStateMachine#fire}，本类不出现 {@code setStatus}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final String STAGE_REPORT = "REPORT";

    private final RepairOrderMapper orderMapper;
    private final RepairOrderImageMapper imageMapper;
    private final RepairCategoryMapper categoryMapper;
    private final LocationService locationService;
    private final OrderStateMachine stateMachine;
    private final OrderNoGenerator orderNoGenerator;
    private final OrderProperties orderProperties;
    private final SysUserMapper userMapper;

    /**
     * 提交报修。
     *
     * <p>质保判定：同楼栋、同故障类型、质保期内（默认 3 天）有已完成工单时，
     * 判定为质保返修 —— 跳过审核直接进入抢单池，并提升优先级（基线 A12）。</p>
     */
    @Transactional
    public OrderDtos.CreateResult create(OrderDtos.CreateRequest req, LoginUser me) {
        if (!Boolean.TRUE.equals(req.privacyConfirmed())) {
            throw new BizException(ResultCode.PRIVACY_NOT_CONFIRMED);
        }
        int maxImages = orderProperties.getMaxImages();
        List<OrderDtos.ImageItem> images = req.images() == null ? List.of() : req.images();
        if (images.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请至少上传 1 张现场照片");
        }
        if (images.size() > maxImages) {
            throw new BizException(ResultCode.IMAGE_LIMIT, "最多上传 " + maxImages + " 张照片");
        }

        RepairCategory category = categoryMapper.selectById(req.categoryId());
        if (category == null || category.getStatus() == null || category.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "故障类型不存在或已停用");
        }

        Location location = locationService.get(req.locationId());
        Long buildingId = locationService.buildingIdOf(location.getId());
        if (buildingId == null) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "所选位置未归属楼栋，请选择到具体的楼栋/房间节点");
        }

        int urgency = req.urgency() == null ? 1 : req.urgency();
        int priority = switch (urgency) {
            case 3 -> 7;
            case 2 -> 4;
            default -> 1;
        };

        RepairOrder warrantySource = findWarrantySource(location.getId(), req.categoryId());
        boolean warranty = warrantySource != null;

        RepairOrder order = new RepairOrder();
        order.setOrderNo(orderNoGenerator.next());
        order.setReporterId(me.getId());
        order.setReporterPhone(StringUtils.hasText(req.reporterPhone())
                ? req.reporterPhone().trim() : phoneOf(me.getId()));
        order.setTitle(req.title().trim());
        order.setDescription(req.description().trim());
        order.setCategoryId(req.categoryId());
        order.setFacilityId(req.facilityId());
        order.setLocationId(location.getId());
        order.setLocationPath(locationService.pathOf(location.getId()));
        order.setBuildingId(buildingId);
        order.setUrgency(urgency);
        order.setPriority(warranty ? Math.min(priority + 1, 9) : priority);
        // 质保单跳过审核直接进抢单池（基线 A12）
        order.setStatus(warranty ? OrderStatus.PENDING.name() : OrderStatus.PENDING_REVIEW.name());
        order.setDeptId(category.getDeptId());
        order.setAutoAssigned(0);
        order.setIsWarranty(warranty ? 1 : 0);
        order.setParentOrderId(warranty ? warrantySource.getId() : null);
        order.setReworkFlag(0);
        order.setReworkCount(0);
        order.setSuspendMinutes(0);
        order.setAutoConfirmed(0);
        order.setVersion(0);
        orderMapper.insert(order);

        int sort = 0;
        for (OrderDtos.ImageItem img : images) {
            RepairOrderImage image = new RepairOrderImage();
            image.setOrderId(order.getId());
            image.setStage(StringUtils.hasText(img.stage()) ? img.stage().toUpperCase() : STAGE_REPORT);
            image.setObjectKey(img.objectKey().trim());
            image.setFileName(img.fileName());
            image.setFileSize(img.fileSize());
            image.setSort(img.sort() == null ? sort : img.sort());
            image.setUploaderId(me.getId());
            imageMapper.insert(image);
            sort++;
        }

        stateMachine.logCreation(order, me);

        String message = warranty
                ? "已提交，该位置 " + orderProperties.getWarrantyDays() + " 天内维修过同类故障，已按质保返修处理"
                : "已提交，等待后勤审核";
        log.info("新建工单 orderNo={} reporter={} warranty={} priority={}",
                order.getOrderNo(), me.getId(), warranty, order.getPriority());
        return new OrderDtos.CreateResult(order.getId(), order.getOrderNo(), order.getStatus(),
                OrderQueryService.labelOf(order.getStatus()), warranty, message);
    }

    /** 审核通过：待审核 → 待处理，进入抢单池。 */
    @Transactional
    public OrderDtos.FireResult review(Long orderId, String remark, LoginUser me) {
        OrderStatus to = stateMachine.fire(orderId, OrderAction.REVIEW_PASS, me, remark, Map.of());
        return fireResult(orderId, to);
    }

    /** 审核驳回：待审核 → 已驳回，原因必填，学生可见。 */
    @Transactional
    public OrderDtos.FireResult reject(Long orderId, String reason, LoginUser me) {
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ResultCode.PARAM_INVALID, "请填写驳回原因");
        }
        OrderStatus to = stateMachine.fire(orderId, OrderAction.REVIEW_REJECT, me, reason, Map.of());
        return fireResult(orderId, to);
    }

    /** 取消：只有管理员可以，任意未完结状态 → 已取消（基线 A9）。 */
    @Transactional
    public OrderDtos.FireResult cancel(Long orderId, String reason, LoginUser me) {
        OrderStatus to = stateMachine.fire(orderId, OrderAction.CANCEL, me,
                StringUtils.hasText(reason) ? reason : "管理员取消", Map.of());
        return fireResult(orderId, to);
    }

    public OrderDtos.FireResult fireResult(Long orderId, OrderStatus status) {
        return new OrderDtos.FireResult(orderId, status.name(), status.getLabel());
    }

    /**
     * 质保来源工单：同位置 + 同故障类型 + 已完成 + 完工时间在质保期内。
     * 取最近一张作为 parent_order_id。
     */
    private RepairOrder findWarrantySource(Long locationId, Long categoryId) {
        LocalDateTime since = LocalDateTime.now().minusDays(orderProperties.getWarrantyDays());
        return orderMapper.selectOne(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getLocationId, locationId)
                .eq(RepairOrder::getCategoryId, categoryId)
                .eq(RepairOrder::getStatus, OrderStatus.COMPLETED.name())
                .ge(RepairOrder::getFinishedAt, since)
                .orderByDesc(RepairOrder::getFinishedAt)
                .last("LIMIT 1"));
    }

    /** 报修人手机号：请求没带时回落到账号档案。 */
    private String phoneOf(Long userId) {
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getPhone();
    }
}
