package com.campusfix.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 编辑用户基本信息：<b>动态 SQL</b>，只更新请求中显式提交的列。
     *
     * <p>约定：{@code null} 字段不出现在 SET 子句（保持原值）；
     * {@code phone}/{@code className} 传空串时经 {@code NULLIF} 置 NULL；
     * {@code deptId} 传 0 时经 {@code NULLIF} 置 NULL（清除部门）。
     * {@code <set>} 自动去除末尾逗号，保证任意字段组合下 SQL 合法。</p>
     *
     * <p>传 {@code expectedUpdatedAt} 时追加乐观校验条件，影响行数为 0 即视为并发冲突。</p>
     *
     * @return 影响行数（0 表示用户不存在/已删除，或乐观校验失败）
     */
    @Update("""
            <script>
            UPDATE sys_user
            <set>
              <if test="req.realName != null">real_name = #{req.realName},</if>
              <if test="req.phone != null">phone = NULLIF(#{req.phone}, ''),</if>
              <if test="req.className != null">class_name = NULLIF(#{req.className}, ''),</if>
              <if test="req.deptId != null">dept_id = NULLIF(#{req.deptId}, 0),</if>
              <if test="req.status != null">status = #{req.status},</if>
              updated_at = NOW()
            </set>
            WHERE id = #{id} AND deleted = 0
            <if test="req.expectedUpdatedAt != null">
              AND updated_at = #{req.expectedUpdatedAt}
            </if>
            </script>
            """)
    int updateBasic(@Param("id") Long id, @Param("req") UserDtos.BasicUpdateRequest req);
}
