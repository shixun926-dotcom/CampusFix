package com.campusfix.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusfix.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT r.code FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.status = 1
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT p.code FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId} AND p.status = 1
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    /** 批量查角色，避免列表页 N+1。 */
    @Select("""
            <script>
            SELECT ur.user_id AS userId, r.code AS code, r.name AS name
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<java.util.Map<String, Object>> selectRolesByUserIds(@Param("ids") List<Long> ids);

    /** 查拥有指定角色（任一）且启用中的用户，用于系统级通知的接收人范围。 */
    @Select("""
            <script>
            SELECT DISTINCT u.*
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.deleted = 0
              AND u.status = 1
              AND r.status = 1
              AND r.code IN
              <foreach collection="roleCodes" item="rc" open="(" separator="," close=")">#{rc}</foreach>
            ORDER BY u.id
            </script>
            """)
    List<SysUser> selectEnabledByRoles(@Param("roleCodes") List<String> roleCodes);
}
