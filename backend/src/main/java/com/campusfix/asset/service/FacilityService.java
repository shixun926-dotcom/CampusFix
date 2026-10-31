package com.campusfix.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campusfix.asset.entity.Facility;
import com.campusfix.asset.entity.FacilityType;
import com.campusfix.asset.mapper.FacilityMapper;
import com.campusfix.asset.mapper.FacilityTypeMapper;
import com.campusfix.common.enums.OrderAction;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.order.entity.RepairOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设施档案：按位置查询 + 与工单状态联动（基线 §9.2）。
 *
 * <p>联动口径（待确认清单 #9 已确认联动）：</p>
 * <ul>
 *   <li>工单进入「处理中」（预约确认）→ 设施置 REPAIRING；</li>
 *   <li>验收完成（确认 / 自动验收）→ 设施回 NORMAL；</li>
 *   <li>其余动作（返修、取消、无法维修）不改动——设施确实还没修好，保持 REPAIRING 更诚实。</li>
 * </ul>
 *
 * <p>联动挂在状态机成功流转之后（见 {@code OrderStateMachine}），失败只记日志。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityMapper facilityMapper;
    private final FacilityTypeMapper facilityTypeMapper;

    /** 某位置下的可用设施（报修时关联选择用；已报废不出现）。 */
    public List<Facility> listByLocation(Long locationId) {
        if (locationId == null || locationId <= 0) {
            return List.of();
        }
        return facilityMapper.selectList(new LambdaQueryWrapper<Facility>()
                .eq(Facility::getLocationId, locationId)
                .ne(Facility::getStatus, "SCRAPPED")
                .orderByAsc(Facility::getFacilityNo));
    }

    public Facility get(Long id) {
        Facility facility = facilityMapper.selectById(id);
        if (facility == null) {
            throw new BizException(ResultCode.NOT_FOUND, "设施不存在");
        }
        return facility;
    }

    /** 设施类型名映射（facility_type 表很小，直接全量）。 */
    public Map<Long, String> typeNameMap() {
        Map<Long, String> map = new HashMap<>();
        facilityTypeMapper.selectList(null).forEach(t -> map.put(t.getId(), t.getName()));
        return map;
    }

    // ------------------------------------------------------------------ 状态联动

    /**
     * 工单流转后的设施状态同步。order 为流转前快照——本联动只关心
     * facilityId 与动作本身，不依赖会被更新改变的字段。
     */
    public void onTransition(RepairOrder order, OrderAction action) {
        try {
            sync(order, action);
        } catch (Exception e) {
            log.warn("设施状态联动失败 orderId={} action={}", order.getId(), action, e);
        }
    }

    private void sync(RepairOrder order, OrderAction action) {
        Long facilityId = order.getFacilityId();
        if (facilityId == null) {
            return;
        }
        switch (action) {
            // 预约确认 → 处理中：设施进入维修
            case SCHEDULE_CONFIRM -> facilityMapper.update(null, new LambdaUpdateWrapper<Facility>()
                    .eq(Facility::getId, facilityId)
                    .set(Facility::getStatus, "REPAIRING"));
            // 验收完成（报修人确认 / 超时自动验收）：设施修复
            case CONFIRM, AUTO_CONFIRM -> facilityMapper.update(null, new LambdaUpdateWrapper<Facility>()
                    .eq(Facility::getId, facilityId)
                    .eq(Facility::getStatus, "REPAIRING")
                    .set(Facility::getStatus, "NORMAL"));
            default -> {
                // 其余动作不联动（返修/取消/无法维修时设施保持 REPAIRING）
            }
        }
    }
}
