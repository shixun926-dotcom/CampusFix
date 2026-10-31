package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.mapper.SysDeptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 部门树。后勤管理员按部门分片管理，数据权限依赖 getDescendantIds。
 */
@Service
@RequiredArgsConstructor
public class DeptService {

    private final SysDeptMapper deptMapper;

    public List<SysDept> tree() {
        List<SysDept> all = deptMapper.selectList(
                new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
        Map<Long, List<SysDept>> byParent = all.stream()
                .collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        all.forEach(d -> {
            List<SysDept> children = byParent.getOrDefault(d.getId(), new ArrayList<>());
            children.sort(Comparator.comparing(SysDept::getSort, Comparator.nullsLast(Integer::compareTo)));
            d.setChildren(children);
        });
        return byParent.getOrDefault(0L, new ArrayList<>());
    }

    public List<SysDept> listAll() {
        return deptMapper.selectList(new LambdaQueryWrapper<SysDept>().orderByAsc(SysDept::getSort));
    }

    /** 部门及其全部下级 ID，用于数据权限。 */
    public Set<Long> getDescendantIds(Long deptId) {
        Set<Long> result = new HashSet<>();
        if (deptId == null) {
            return result;
        }
        result.add(deptId);
        List<SysDept> all = deptMapper.selectList(null);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SysDept d : all) {
                if (d.getParentId() != null && result.contains(d.getParentId()) && result.add(d.getId())) {
                    changed = true;
                }
            }
        }
        return result;
    }

    public SysDept create(SysDept dept) {
        dept.setId(null);
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        deptMapper.insert(dept);
        return dept;
    }

    public void update(Long id, SysDept dept) {
        SysDept exist = deptMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "部门不存在");
        }
        if (id.equals(dept.getParentId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "上级部门不能是自己");
        }
        dept.setId(id);
        deptMapper.updateById(dept);
    }

    public void delete(Long id) {
        Long children = deptMapper.selectCount(
                new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "存在下级部门，无法删除");
        }
        deptMapper.deleteById(id);
    }

    public String nameOf(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDept dept = deptMapper.selectById(deptId);
        return dept == null ? null : dept.getName();
    }
}
