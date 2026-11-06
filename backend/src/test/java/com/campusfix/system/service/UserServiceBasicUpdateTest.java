package com.campusfix.system.service;

import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.JwtTokenProvider;
import com.campusfix.common.security.LoginUser;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysRole;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 编辑用户基本信息（动态 SQL 更新）的单元测试。
 *
 * <p>聚焦 Service 层业务规则：部分更新的字段语义、角色/班级/部门一致性、
 * 自我保护与「最后一个管理员」保护、影响行数判定与令牌注销副作用。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceBasicUpdateTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysCreditLogMapper creditLogMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;

    private UserService service;

    private static final Long ADMIN_ID = 1L;      // 当前登录的管理员
    private static final Long TARGET_ID = 20L;    // 被编辑的维修工

    @BeforeEach
    void setUp() {
        service = new UserService(userMapper, userRoleMapper, roleMapper, deptMapper,
                creditLogMapper, passwordEncoder, tokenProvider);
        loginAs(ADMIN_ID, "ADMIN");
        // detail() 组装 VO 时的公共依赖
        lenient().when(roleMapper.selectList(any())).thenReturn(List.of(
                role(1L, "STUDENT", "学生"),
                role(2L, "REPAIRER", "维修人员"),
                role(3L, "MANAGER", "后勤管理员"),
                role(4L, "ADMIN", "系统管理员")));
        lenient().when(deptMapper.selectList(any())).thenReturn(List.of(dept(2L, "水电维修组")));
        // 部门有效性校验：默认返回启用中的部门（各用例可覆盖）
        lenient().when(deptMapper.selectById(anyLong())).thenReturn(dept(2L, "水电维修组"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- 正常路径

    @Test
    @DisplayName("只改姓名：动态 SQL 仅携带 realName，其余列为 null 不参与更新")
    void updateOnlyRealName() {
        SysUser exist = user(TARGET_ID, "张建国", null, null, 2L, 1);
        stubTarget(exist, "REPAIRER");
        when(userMapper.updateBasic(eq(TARGET_ID), any())).thenReturn(1);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRealName("张建军");

        service.updateBasic(TARGET_ID, req);

        ArgumentCaptor<UserDtos.BasicUpdateRequest> captor =
                ArgumentCaptor.forClass(UserDtos.BasicUpdateRequest.class);
        verify(userMapper).updateBasic(eq(TARGET_ID), captor.capture());
        UserDtos.BasicUpdateRequest patch = captor.getValue();
        assertThat(patch.getRealName()).isEqualTo("张建军");
        assertThat(patch.getPhone()).isNull();
        assertThat(patch.getClassName()).isNull();
        assertThat(patch.getDeptId()).isNull();
        assertThat(patch.getStatus()).isNull();
        // 未变更角色与部门，不应触发令牌注销
        verify(tokenProvider, never()).logoutAll(anyLong());
    }

    @Test
    @DisplayName("手机号传空串：patch 中保留空串，由 SQL 的 NULLIF 置空")
    void clearPhoneWithEmptyString() {
        SysUser exist = user(TARGET_ID, "张建国", "13900000001", null, 2L, 1);
        stubTarget(exist, "REPAIRER");
        when(userMapper.updateBasic(eq(TARGET_ID), any())).thenReturn(1);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setPhone("");

        service.updateBasic(TARGET_ID, req);

        ArgumentCaptor<UserDtos.BasicUpdateRequest> captor =
                ArgumentCaptor.forClass(UserDtos.BasicUpdateRequest.class);
        verify(userMapper).updateBasic(eq(TARGET_ID), captor.capture());
        assertThat(captor.getValue().getPhone()).isEmpty();
    }

    @Test
    @DisplayName("角色变更为管理员：重新绑定角色并注销该用户令牌")
    void roleChangeAssignsRolesAndLogsOut() {
        SysUser exist = user(TARGET_ID, "张建国", null, null, 2L, 1);
        stubTarget(exist, "REPAIRER");
        when(roleMapper.selectCount(any())).thenReturn(1L);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRoleCodes(List.of("MANAGER"));

        service.updateBasic(TARGET_ID, req);

        // 仅角色变化时主表无需更新（动态 SQL 不应产生空转写）
        verify(userMapper, never()).updateBasic(anyLong(), any());
        verify(userRoleMapper).deleteByUserId(TARGET_ID);
        verify(userRoleMapper).insert(eq(TARGET_ID), eq(3L));
        verify(tokenProvider).logoutAll(TARGET_ID);
    }

    // ---------------------------------------------------------------- 参数与存在性

    @Test
    @DisplayName("空提交：拒绝并提示无更新内容")
    void rejectEmptyRequest() {
        stubTarget(user(TARGET_ID, "张建国", null, null, 2L, 1), "REPAIRER");

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, new UserDtos.BasicUpdateRequest()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_INVALID.getCode()));
        verify(userMapper, never()).updateBasic(anyLong(), any());
    }

    @Test
    @DisplayName("用户不存在：返回 4001")
    void userNotFound() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.updateBasic(999L, reqWithName("张三")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("影响行数为 0 且未启用乐观校验：视为用户不存在")
    void zeroRowsTreatedAsNotFound() {
        stubTarget(user(TARGET_ID, "张建国", null, null, 2L, 1), "REPAIRER");
        when(userMapper.updateBasic(eq(TARGET_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, reqWithName("张建军")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("影响行数为 0 且启用乐观校验：返回 3013 并发冲突")
    void zeroRowsWithOptimisticLockReturnsConflict() {
        stubTarget(user(TARGET_ID, "张建国", null, null, 2L, 1), "REPAIRER");
        when(userMapper.updateBasic(eq(TARGET_ID), any())).thenReturn(0);

        UserDtos.BasicUpdateRequest req = reqWithName("张建军");
        req.setExpectedUpdatedAt(java.time.LocalDateTime.now().minusHours(1));

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_CHANGED.getCode()));
    }

    // ---------------------------------------------------------------- 自我保护

    @Test
    @DisplayName("禁用当前登录账号：拒绝（3001）")
    void cannotDisableSelf() {
        stubTarget(user(ADMIN_ID, "系统管理员", null, null, 1L, 1), "ADMIN");

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setStatus(0);

        assertThatThrownBy(() -> service.updateBasic(ADMIN_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("移除自己的管理员角色：拒绝（3001）")
    void cannotDropOwnAdminRole() {
        stubTarget(user(ADMIN_ID, "系统管理员", null, null, 1L, 1), "ADMIN");
        when(roleMapper.selectCount(any())).thenReturn(1L);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRoleCodes(List.of("MANAGER"));

        assertThatThrownBy(() -> service.updateBasic(ADMIN_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_NOT_ALLOWED.getCode()));
    }

    @Test
    @DisplayName("禁用最后一个启用的管理员：拒绝（3001）")
    void cannotDisableLastAdmin() {
        // 目标是另一位管理员，且除他之外没有启用中的管理员
        stubTarget(user(TARGET_ID, "另一管理员", null, null, 1L, 1), "ADMIN");
        when(userMapper.selectCount(any())).thenReturn(0L);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setStatus(0);

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.STATUS_NOT_ALLOWED.getCode()));
    }

    // ---------------------------------------------------------------- 角色与班级/部门一致性

    @Test
    @DisplayName("学生角色但未填班级：拒绝（1003）")
    void studentRequiresClassName() {
        stubTarget(user(TARGET_ID, "陈晓", null, null, null, 1), "STUDENT");
        when(roleMapper.selectCount(any())).thenReturn(1L);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRoleCodes(List.of("STUDENT"));

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_INVALID.getCode()));
    }

    @Test
    @DisplayName("教职工角色但无部门：拒绝（1003）")
    void staffRequiresDept() {
        stubTarget(user(TARGET_ID, "张建国", null, null, null, 1), "REPAIRER");
        when(roleMapper.selectCount(any())).thenReturn(1L);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRoleCodes(List.of("REPAIRER"));

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_INVALID.getCode()));
    }

    @Test
    @DisplayName("学生与教职工角色并存：拒绝（1003）")
    void studentAndStaffMutuallyExclusive() {
        stubTarget(user(TARGET_ID, "张建国", "计算机 2201", null, 2L, 1), "REPAIRER");
        when(roleMapper.selectCount(any())).thenReturn(2L);

        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRoleCodes(List.of("STUDENT", "REPAIRER"));

        assertThatThrownBy(() -> service.updateBasic(TARGET_ID, req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_INVALID.getCode()));
    }

    // ---------------------------------------------------------------- 工具

    private void loginAs(Long id, String role) {
        LoginUser user = new LoginUser(id, "u" + id, "用户" + id);
        user.setRoles(new LinkedHashSet<>(List.of(role)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void stubTarget(SysUser exist, String role) {
        when(userMapper.selectById(exist.getId())).thenReturn(exist);
        lenient().when(userMapper.selectRoleCodes(exist.getId())).thenReturn(List.of(role));
    }

    private SysUser user(Long id, String realName, String phone, String className, Long deptId, Integer status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRealName(realName);
        u.setPhone(phone);
        u.setClassName(className);
        u.setDeptId(deptId);
        u.setStatus(status);
        u.setCreditScore(100);
        return u;
    }

    private SysRole role(Long id, String code, String name) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setCode(code);
        r.setName(name);
        r.setStatus(1);
        return r;
    }

    private SysDept dept(Long id, String name) {
        SysDept d = new SysDept();
        d.setId(id);
        d.setName(name);
        d.setStatus(1);
        return d;
    }

    private UserDtos.BasicUpdateRequest reqWithName(String realName) {
        UserDtos.BasicUpdateRequest req = new UserDtos.BasicUpdateRequest();
        req.setRealName(realName);
        return req;
    }
}
