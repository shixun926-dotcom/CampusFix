package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.system.entity.SysPermission;
import com.campusfix.system.entity.SysRole;
import com.campusfix.system.mapper.SysPermissionMapper;
import com.campusfix.system.mapper.SysRoleMapper;
import com.campusfix.system.mapper.SysRolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色与权限。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public List<SysRole> listAll() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public List<Long> permissionIdsOf(Long roleId) {
        return rolePermissionMapper.selectPermissionIds(roleId);
    }

    /** 权限树。 */
    public List<SysPermission> permissionTree() {
        List<SysPermission> all = permissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort));
        Map<Long, List<SysPermission>> byParent = all.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        all.forEach(p -> p.setChildren(byParent.getOrDefault(p.getId(), new ArrayList<>())));
        return byParent.getOrDefault(0L, new ArrayList<>());
    }

    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "角色不存在");
        }
        rolePermissionMapper.deleteByRoleId(roleId);
        if (permissionIds == null) {
            return;
        }
        permissionIds.forEach(pid -> rolePermissionMapper.insert(roleId, pid));
    }
}
