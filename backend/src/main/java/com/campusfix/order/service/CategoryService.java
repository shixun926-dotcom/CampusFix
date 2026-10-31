package com.campusfix.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.order.dto.OrderDtos;
import com.campusfix.order.entity.RepairCategory;
import com.campusfix.order.mapper.RepairCategoryMapper;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 故障类型：报修表单下拉、责任部门推导。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final RepairCategoryMapper categoryMapper;
    private final SysDeptMapper deptMapper;

    public List<OrderDtos.CategoryVO> listEnabled() {
        Map<Long, String> depts = deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
        return categoryMapper.selectList(new LambdaQueryWrapper<RepairCategory>()
                        .eq(RepairCategory::getStatus, 1)
                        .orderByAsc(RepairCategory::getSort))
                .stream()
                .map(c -> new OrderDtos.CategoryVO(c.getId(), c.getName(), c.getCode(), c.getDeptId(),
                        depts.get(c.getDeptId())))
                .toList();
    }

    public RepairCategory getEnabled(Long id) {
        RepairCategory c = categoryMapper.selectById(id);
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BizException(ResultCode.PARAM_INVALID, "故障类型不存在或已停用");
        }
        return c;
    }
}
