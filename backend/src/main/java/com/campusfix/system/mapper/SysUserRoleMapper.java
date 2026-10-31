package com.campusfix.system.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户-角色关联表（复合主键 user_id + role_id）。
 *
 * <p>关联表没有单一主键，因此**不继承 BaseMapper**：MP 的 xxById 系列方法依赖 @TableId，
 * 继承会触发 "Can not find table primary key" 告警且方法不可用。
 * 这里直接声明所需的 3 条 SQL，语义更明确。</p>
 */
@Mapper
public interface SysUserRoleMapper {

    /** 幂等插入：重复分配同一角色不报错。 */
    @Insert("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);
}
