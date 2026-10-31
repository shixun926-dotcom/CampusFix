package com.campusfix.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusfix.system.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT DISTINCT p.* FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId} AND p.status = 1
            ORDER BY p.sort
            """)
    List<SysPermission> selectByUserId(@Param("userId") Long userId);
}
