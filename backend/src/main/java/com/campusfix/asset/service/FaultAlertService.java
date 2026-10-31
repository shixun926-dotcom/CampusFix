package com.campusfix.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.asset.entity.FaultAlert;
import com.campusfix.asset.mapper.FaultAlertMapper;
import com.campusfix.common.config.StatsProperties;
import com.campusfix.common.enums.OrderStatus;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.order.dto.OrderDtos.CategoryVO;
import com.campusfix.order.entity.RepairOrder;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.order.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 高频故障预警（基线 §12.3）。
 *
 * <p>判定规则：同一楼栋（BUILDING 级）+ 最近 N 天 + 同一故障类型 + 工单数 ≥ 阈值。
 * 取消与驳回的工单不计入（不是真实故障）。每天 08:00 定时扫描，
 * 也提供手动触发接口便于运维验证；{@code uk_day_combo} 唯一索引保证同窗口幂等。</p>
 *
 * <p>只做提示，不自动建批量任务（基线明确按人工处理）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaultAlertService {

    private final FaultAlertMapper alertMapper;
    private final RepairOrderMapper orderMapper;
    private final StatsProperties statsProperties;
    private final LocationService locationService;
    private final CategoryService categoryService;

    /**
     * 扫描并落库预警，返回本次新增条数。重复执行安全（唯一索引兜底）。
     */
    public int scan() {
        int days = Math.max(1, statsProperties.getFaultAlertDays());
        int threshold = Math.max(1, statsProperties.getFaultAlertThreshold());
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);

        List<Map<String, Object>> rows = orderMapper.selectMaps(new QueryWrapper<RepairOrder>()
                .select("building_id AS bid", "category_id AS cid", "COUNT(*) AS cnt")
                .ge("created_at", start.atStartOfDay())
                .notIn("status", OrderStatus.CANCELLED.name(), OrderStatus.REJECTED.name())
                .isNotNull("building_id")
                .isNotNull("category_id")
                .groupBy("building_id", "category_id")
                .having("COUNT(*) >= " + threshold));

        int inserted = 0;
        for (Map<String, Object> row : rows) {
            FaultAlert alert = new FaultAlert();
            alert.setBuildingId(((Number) row.get("bid")).longValue());
            alert.setCategoryId(((Number) row.get("cid")).longValue());
            alert.setOrderCount(((Number) row.get("cnt")).intValue());
            alert.setWindowStart(start);
            alert.setWindowEnd(end);
            alert.setStatus("NEW");
            try {
                alertMapper.insert(alert);
                inserted++;
                log.info("[FaultAlert] 高频故障预警 楼栋={} 类型={} 近{}天 {} 单",
                        alert.getBuildingId(), alert.getCategoryId(), days, alert.getOrderCount());
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 同楼栋同类型同窗口已预警过，跳过
            }
        }
        return inserted;
    }

    /** 预警列表（附楼栋 / 类型名称），status 为空返回全部。 */
    public List<FaultAlertVO> list(String status, int limit) {
        LambdaQueryWrapper<FaultAlert> w = new LambdaQueryWrapper<FaultAlert>()
                .eq(StringUtils.hasText(status), FaultAlert::getStatus, status)
                .orderByDesc(FaultAlert::getId)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100));
        List<FaultAlert> alerts = alertMapper.selectList(w);
        if (alerts.isEmpty()) {
            return List.of();
        }
        Map<Long, String> buildings = locationService.nameMap();
        Map<Long, String> categories = new HashMap<>();
        for (CategoryVO c : categoryService.listEnabled()) {
            categories.put(c.id(), c.name());
        }
        return alerts.stream().map(a -> toVO(a, buildings, categories)).toList();
    }

    /** 未处理（NEW）预警数，管理端横幅用。 */
    public long newCount() {
        Long count = alertMapper.selectCount(new LambdaQueryWrapper<FaultAlert>()
                .eq(FaultAlert::getStatus, "NEW"));
        return count == null ? 0 : count;
    }

    /** 标记预警状态：READ 已读 / HANDLED 已处理。 */
    public void updateStatus(Long id, String status) {
        if (!Set.of("READ", "HANDLED").contains(status)) {
            throw new BizException(ResultCode.PARAM_INVALID, "预警状态只允许 READ / HANDLED");
        }
        int rows = alertMapper.update(null, new LambdaUpdateWrapper<FaultAlert>()
                .eq(FaultAlert::getId, id)
                .set(FaultAlert::getStatus, status));
        if (rows == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "预警不存在");
        }
    }

    private FaultAlertVO toVO(FaultAlert a, Map<Long, String> buildings, Map<Long, String> categories) {
        String text = buildings.getOrDefault(a.getBuildingId(), "未知楼栋")
                + "近 7 天出现 " + a.getOrderCount() + " 单「"
                + categories.getOrDefault(a.getCategoryId(), "未知类型")
                + "」，建议检查公共设备或线路。";
        return new FaultAlertVO(a.getId(), a.getBuildingId(), a.getCategoryId(),
                buildings.getOrDefault(a.getBuildingId(), "未知楼栋"),
                categories.getOrDefault(a.getCategoryId(), "未知类型"),
                a.getOrderCount(), a.getWindowStart() == null ? null : a.getWindowStart().toString(),
                a.getWindowEnd() == null ? null : a.getWindowEnd().toString(),
                a.getStatus(), text, a.getCreatedAt() == null ? null : a.getCreatedAt().toString());
    }

    /** 预警 VO：content 为可直接展示的提示文案（基线示例样式）。 */
    public record FaultAlertVO(Long id, Long buildingId, Long categoryId,
                               String buildingName, String categoryName,
                               Integer orderCount, String windowStart, String windowEnd,
                               String status, String content, String createdAt) {
    }
}
