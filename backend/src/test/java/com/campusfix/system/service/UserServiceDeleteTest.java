package com.campusfix.system.service;

import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.JwtTokenProvider;
import com.campusfix.common.security.LoginUser;
import com.campusfix.order.mapper.RepairOrderMapper;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysCreditLogMapper;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysRoleMapper;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成员删除 / 批量删除的单元测试。
 *
 * <p>聚焦 Service 层的删除护栏：不能删自己、不能删最后一个启用的管理员、
 * 有未完结工单不能删；以及批量删除「逐条成功或跳过、返回失败原因」的语义。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceDeleteTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysCreditLogMapper creditLogMapper;
    @Mock private RepairOrderMapper repairOrderMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;

    private UserService service;

    private static final Long OPERATOR_ID = 1L;
    private static final Long TARGET_ID = 20L;

    @BeforeEach
    void setUp() {
        service = new UserService(userMapper, userRoleMapper, roleMapper, deptMapper,
                creditLogMapper, repairOrderMapper, passwordEncoder, tokenProvider);
        loginAs(OPERATOR_ID, "ADMIN");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- 正常路径

    @Test
    @DisplayName("删除普通成员：执行逻辑删除并注销其令牌")
    void deleteNormalMember() {
        SysUser target = user(TARGET_ID, "陈晓", 1);
        when(userMapper.selectById(TARGET_ID)).thenReturn(target);
        when(userMapper.selectRoleCodes(TARGET_ID)).thenReturn(List.of("STUDENT"));
        when(repairOrderMapper.countActiveByUser(TARGET_ID)).thenReturn(0);
        when(userMapper.logicalDeleteById(TARGET_ID)).thenReturn(1);

        service.delete(TARGET_ID);

        verify(userMapper).logicalDeleteById(TARGET_ID);
        verify(tokenProvider).logoutAll(TARGET_ID);
    }

    @Test
    @DisplayName("删除维修工：存在未完结工单时拒绝，且不写库")
    void rejectWhenActiveOrdersExist() {
        SysUser target = user(TARGET_ID, "张建国", 1);
        when(userMapper.selectById(TARGET_ID)).thenReturn(target);
        when(userMapper.selectRoleCodes(TARGET_ID)).thenReturn(List.of("REPAIRER"));
        when(repairOrderMapper.countActiveByUser(TARGET_ID)).thenReturn(3);

        assertThatThrownBy(() -> service.delete(TARGET_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("3 张未完结工单");

        verify(userMapper, never()).logicalDeleteById(anyLong());
        verify(tokenProvider, never()).logoutAll(anyLong());
    }

    // ---------------------------------------------------------------- 护栏

    @Test
    @DisplayName("不能删除当前登录账号")
    void rejectSelfDelete() {
        SysUser self = user(OPERATOR_ID, "林振华", 1);
        when(userMapper.selectById(OPERATOR_ID)).thenReturn(self);

        assertThatThrownBy(() -> service.delete(OPERATOR_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能删除当前登录账号");
    }

    @Test
    @DisplayName("最后一个启用的系统管理员不可删除")
    void rejectLastActiveAdmin() {
        SysUser admin = user(TARGET_ID, "另一管理员", 1);
        when(userMapper.selectById(TARGET_ID)).thenReturn(admin);
        when(userMapper.selectRoleCodes(TARGET_ID)).thenReturn(List.of("ADMIN"));
        // 除他之外没有其他启用管理员
        when(userMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.delete(TARGET_ID))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("系统至少保留一名启用的系统管理员");

        verify(repairOrderMapper, never()).countActiveByUser(anyLong());
    }

    @Test
    @DisplayName("还有其他启用管理员时，可以删除非最后一个管理员")
    void allowDeletingAdminWhenAnotherExists() {
        SysUser admin = user(TARGET_ID, "另一管理员", 1);
        when(userMapper.selectById(TARGET_ID)).thenReturn(admin);
        when(userMapper.selectRoleCodes(TARGET_ID)).thenReturn(List.of("ADMIN"));
        when(userMapper.selectCount(any())).thenReturn(1L);
        when(repairOrderMapper.countActiveByUser(TARGET_ID)).thenReturn(0);
        when(userMapper.logicalDeleteById(TARGET_ID)).thenReturn(1);

        service.delete(TARGET_ID);

        verify(userMapper).logicalDeleteById(TARGET_ID);
    }

    @Test
    @DisplayName("成员不存在（或已删除）返回 NOT_FOUND")
    void rejectMissingUser() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(TARGET_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("影响行数为 0（并发已删）视为不存在")
    void rejectWhenNothingUpdated() {
        SysUser target = user(TARGET_ID, "陈晓", 1);
        when(userMapper.selectById(TARGET_ID)).thenReturn(target);
        when(userMapper.selectRoleCodes(TARGET_ID)).thenReturn(List.of("STUDENT"));
        when(repairOrderMapper.countActiveByUser(TARGET_ID)).thenReturn(0);
        when(userMapper.logicalDeleteById(TARGET_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.delete(TARGET_ID))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    // ---------------------------------------------------------------- 批量

    @Test
    @DisplayName("批量删除：能删的删掉，不能删的记录原因，不因一条失败中断整批")
    void batchDeleteKeepsPartialSuccess() {
        Long okId = 21L;
        Long busyId = 22L;
        Long selfId = OPERATOR_ID;

        when(userMapper.selectById(okId)).thenReturn(user(okId, "陈晓", 1));
        when(userMapper.selectRoleCodes(okId)).thenReturn(List.of("STUDENT"));

        when(userMapper.selectById(busyId)).thenReturn(user(busyId, "张建国", 1));
        when(userMapper.selectRoleCodes(busyId)).thenReturn(List.of("REPAIRER"));
        when(repairOrderMapper.countActiveByUser(busyId)).thenReturn(2);

        when(userMapper.selectById(selfId)).thenReturn(user(selfId, "林振华", 1));

        when(repairOrderMapper.countActiveByUser(okId)).thenReturn(0);
        when(userMapper.logicalDeleteById(okId)).thenReturn(1);

        UserDtos.DeleteResult result = service.deleteBatch(List.of(okId, busyId, selfId));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).hasSize(2);
        assertThat(result.getFailed())
                .extracting(UserDtos.DeleteFailure::getId)
                .containsExactlyInAnyOrder(busyId, selfId);
        assertThat(result.getFailed())
                .extracting(UserDtos.DeleteFailure::getReason)
                .anySatisfy(r -> assertThat(r).contains("未完结工单"))
                .anySatisfy(r -> assertThat(r).contains("不能删除当前登录账号"));

        verify(userMapper).logicalDeleteById(okId);
        verify(userMapper, never()).logicalDeleteById(busyId);
        verify(tokenProvider).logoutAll(okId);
    }

    @Test
    @DisplayName("批量删除会去重并忽略 null，避免同一条重复执行")
    void batchDeleteDeduplicatesIds() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "陈晓", 1));
        when(userMapper.selectRoleCodes(TARGET_ID)).thenReturn(List.of("STUDENT"));
        when(repairOrderMapper.countActiveByUser(TARGET_ID)).thenReturn(0);
        when(userMapper.logicalDeleteById(TARGET_ID)).thenReturn(1);

        UserDtos.DeleteResult result = service.deleteBatch(
                java.util.Arrays.asList(TARGET_ID, TARGET_ID, null, TARGET_ID));

        assertThat(result.getSuccess()).isEqualTo(1);
        assertThat(result.getFailed()).isEmpty();
        verify(userMapper).logicalDeleteById(TARGET_ID);
    }

    // ---------------------------------------------------------------- 工具

    private void loginAs(Long id, String role) {
        LoginUser user = new LoginUser(id, "u" + id, "用户" + id);
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private SysUser user(Long id, String realName, Integer status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRealName(realName);
        u.setStatus(status);
        u.setCreditScore(100);
        return u;
    }
}
