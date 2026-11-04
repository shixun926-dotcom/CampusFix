package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.JwtTokenProvider;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.entity.SysCreditLog;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysRole;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysCreditLogMapper;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysRoleMapper;
import com.campusfix.system.mapper.SysUserMapper;
import com.campusfix.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户服务：CRUD、启停、重置密码、角色分配、信誉分增减。
 *
 * <p>信誉分只能通过 {@link #adjustCredit} 修改，并写 sys_credit_log 便于追溯。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_PASSWORD = "Cf@123456";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final SysCreditLogMapper creditLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public PageResult<UserDtos.UserVO> page(UserDtos.Query query) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            wrapper.and(w -> w.like(SysUser::getUsername, kw)
                    .or().like(SysUser::getRealName, kw)
                    .or().like(SysUser::getPhone, kw));
        }
        if (query.getDeptId() != null) {
            wrapper.eq(SysUser::getDeptId, query.getDeptId());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysUser::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getRoleCode())) {
            wrapper.inSql(SysUser::getId,
                    "SELECT user_id FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id WHERE r.code = '"
                            + query.getRoleCode().replace("'", "") + "'");
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);

        IPage<SysUser> page = userMapper.selectPage(
                new Page<>(query.getPage(), Math.min(query.getSize(), 100)), wrapper);

        Map<Long, List<String>> roleCodes = batchRoleCodes(page.getRecords().stream().map(SysUser::getId).toList());
        Map<Long, String> deptNames = deptNameMap();
        Map<Long, List<String>> roleNames = batchRoleNames(page.getRecords().stream().map(SysUser::getId).toList());

        List<UserDtos.UserVO> list = page.getRecords().stream()
                .map(u -> toVO(u, roleCodes.getOrDefault(u.getId(), List.of()),
                        roleNames.getOrDefault(u.getId(), List.of()), deptNames))
                .toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public UserDtos.UserVO detail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user, roleCodesOf(id), roleNamesOf(id), deptNameMap());
    }

    @Transactional
    public Long create(UserDtos.SaveRequest req) {
        checkUnique(req.getUsername(), req.getStudentNo(), req.getStaffNo(), null);
        SysUser user = new SysUser();
        user.setUsername(req.getUsername().trim());
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(req.getRealName());
        user.setStudentNo(req.getStudentNo());
        user.setStaffNo(req.getStaffNo());
        user.setDeptId(req.getDeptId());
        user.setPhone(req.getPhone());
        user.setCreditScore(100);
        user.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        user.setPwdResetFlag(1);
        // 头像强制更换：创建时约定 1 天内需更换（基线：头像默认值 + 一天内强制更换）
        user.setAvatarDeadline(LocalDateTime.now().plusDays(1));
        userMapper.insert(user);
        assignRoles(user.getId(), req.getRoleCodes());
        return user.getId();
    }

    @Transactional
    public void update(Long id, UserDtos.SaveRequest req) {
        SysUser exist = userMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        checkUnique(req.getUsername(), req.getStudentNo(), req.getStaffNo(), id);
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(req.getUsername().trim());
        user.setRealName(req.getRealName());
        user.setStudentNo(req.getStudentNo());
        user.setStaffNo(req.getStaffNo());
        user.setDeptId(req.getDeptId());
        user.setPhone(req.getPhone());
        user.setStatus(req.getStatus());
        userMapper.updateById(user);
        if (req.getRoleCodes() != null) {
            assignRoles(id, req.getRoleCodes());
            tokenProvider.logoutAll(id);
        }
    }

    /**
     * 手动新增单个学生：账号 = 学号，角色固定为学生，初始密码需首次登录修改。
     * 与批量导入走同一套唯一性校验，避免两条入口规则不一致。
     */
    @Transactional
    public Long createStudent(UserDtos.StudentCreateRequest req) {
        String studentNo = req.studentNo().trim();
        Long dup = userMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getStudentNo, studentNo));
        if (dup != null && dup > 0) {
            throw new BizException(ResultCode.DUPLICATE, "学号已存在：" + studentNo);
        }
        checkUnique(studentNo, studentNo, null, null);

        SysUser user = new SysUser();
        user.setUsername(studentNo);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setRealName(req.realName().trim());
        user.setStudentNo(studentNo);
        user.setClassName(req.className().trim());
        user.setPhone(StringUtils.hasText(req.phone()) ? req.phone().trim() : null);
        user.setCreditScore(100);
        user.setStatus(1);
        user.setPwdResetFlag(1);
        user.setAvatarDeadline(LocalDateTime.now().plusDays(1));
        userMapper.insert(user);

        assignRoles(user.getId(), List.of(RoleCode.STUDENT));
        return user.getId();
    }

    /** 启停用：禁用后立即踢下线。 */
    public void updateStatus(Long id, Integer status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
        if (status != null && status == 0) {
            tokenProvider.logoutAll(id);
        }
    }

    public String resetPassword(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setPwdResetFlag(1);
        userMapper.updateById(user);
        tokenProvider.logoutAll(id);
        return DEFAULT_PASSWORD;
    }

    /**
     * 信誉分调整：唯一入口，写流水。
     *
     * @param delta      正数加分、负数扣分
     * @param bizId      关联业务（通常为工单）
     * @param operatorId 操作人，null 表示系统自动
     */
    @Transactional
    public int adjustCredit(Long userId, int delta, String reason, Long bizId, Long operatorId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int before = user.getCreditScore() == null ? 100 : user.getCreditScore();
        int after = Math.max(0, Math.min(100, before + delta));

        SysUser update = new SysUser();
        update.setId(userId);
        update.setCreditScore(after);
        userMapper.updateById(update);

        SysCreditLog log = new SysCreditLog();
        log.setUserId(userId);
        log.setDelta(after - before);
        log.setBeforeScore(before);
        log.setAfterScore(after);
        log.setReason(reason);
        log.setBizId(bizId);
        log.setOperatorId(operatorId);
        log.setCreatedAt(java.time.LocalDateTime.now());
        creditLogMapper.insert(log);
        return after;
    }

    public SysUser getById(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    public List<String> roleCodesOf(Long userId) {
        return userMapper.selectRoleCodes(userId);
    }

    public List<String> roleNamesOf(Long userId) {
        return roleMapper.selectList(null).stream()
                .filter(r -> roleCodesOf(userId).contains(r.getCode()))
                .map(SysRole::getName)
                .toList();
    }

    @Transactional
    public void assignRoles(Long userId, List<String> roleCodes) {
        if (roleCodes == null) {
            return;
        }
        userRoleMapper.deleteByUserId(userId);
        if (roleCodes.isEmpty()) {
            return;
        }
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().in(SysRole::getCode, roleCodes));
        for (SysRole role : roles) {
            userRoleMapper.insert(userId, role.getId());
        }
    }

    private void checkUnique(String username, String studentNo, String staffNo, Long excludeId) {
        LambdaQueryWrapper<SysUser> w1 = new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username);
        if (excludeId != null) {
            w1.ne(SysUser::getId, excludeId);
        }
        if (userMapper.selectCount(w1) > 0) {
            throw new BizException(ResultCode.DUPLICATE, "账号已存在：" + username);
        }
        if (StringUtils.hasText(studentNo)) {
            LambdaQueryWrapper<SysUser> w2 = new LambdaQueryWrapper<SysUser>().eq(SysUser::getStudentNo, studentNo);
            if (excludeId != null) {
                w2.ne(SysUser::getId, excludeId);
            }
            if (userMapper.selectCount(w2) > 0) {
                throw new BizException(ResultCode.DUPLICATE, "学号已存在：" + studentNo);
            }
        }
        if (StringUtils.hasText(staffNo)) {
            LambdaQueryWrapper<SysUser> w3 = new LambdaQueryWrapper<SysUser>().eq(SysUser::getStaffNo, staffNo);
            if (excludeId != null) {
                w3.ne(SysUser::getId, excludeId);
            }
            if (userMapper.selectCount(w3) > 0) {
                throw new BizException(ResultCode.DUPLICATE, "工号已存在：" + staffNo);
            }
        }
    }

    private Map<Long, List<String>> batchRoleCodes(List<Long> userIds) {
        Map<Long, List<String>> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        userMapper.selectRolesByUserIds(userIds).forEach(row -> {
            Long uid = ((Number) row.get("userId")).longValue();
            map.computeIfAbsent(uid, k -> new ArrayList<>()).add(String.valueOf(row.get("code")));
        });
        return map;
    }

    private Map<Long, List<String>> batchRoleNames(List<Long> userIds) {
        Map<Long, List<String>> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        userMapper.selectRolesByUserIds(userIds).forEach(row -> {
            Long uid = ((Number) row.get("userId")).longValue();
            map.computeIfAbsent(uid, k -> new ArrayList<>()).add(String.valueOf(row.get("name")));
        });
        return map;
    }

    private Map<Long, String> deptNameMap() {
        return deptMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getName, (a, b) -> a));
    }

    private UserDtos.UserVO toVO(SysUser u, List<String> codes, List<String> names, Map<Long, String> deptNames) {
        UserDtos.UserVO vo = new UserDtos.UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setStudentNo(u.getStudentNo());
        vo.setClassName(u.getClassName());
        vo.setStaffNo(u.getStaffNo());
        vo.setDeptId(u.getDeptId());
        vo.setDeptName(u.getDeptId() == null ? null : deptNames.get(u.getDeptId()));
        vo.setPhone(u.getPhone());
        vo.setAvatar(u.getAvatar());
        vo.setCreditScore(u.getCreditScore());
        vo.setStatus(u.getStatus());
        vo.setRoleCodes(codes);
        vo.setRoleNames(names);
        vo.setLastLoginAt(u.getLastLoginAt());
        vo.setCreatedAt(u.getCreatedAt());
        return vo;
    }

    @SuppressWarnings("unused")
    private <T> Map<Long, T> index(List<T> list, Function<T, Long> key) {
        return list.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
    }
}
