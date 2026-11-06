package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.common.security.JwtTokenProvider;
import com.campusfix.order.mapper.RepairOrderMapper;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final RepairOrderMapper repairOrderMapper;
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
     * 编辑用户基本信息（部分更新，动态 SQL）。
     *
     * <p>字段语义见 {@link UserDtos.BasicUpdateRequest}：null 不修改、空串清除、
     * deptId=0 清除部门。与既有 {@link #update} 的全量覆盖不同，本方法只写
     * 「本次提交且确有变化」的列。</p>
     *
     * <p>业务规则（违反即拒绝）：</p>
     * <ol>
     *   <li>至少保留一个角色，且角色必须存在并启用；</li>
     *   <li>学生与教职工角色互斥（STUDENT 不可与 REPAIRER/MANAGER/ADMIN 并存）；</li>
     *   <li>学生必须填写班级且不挂部门；教职工必须选择存在的启用部门且不挂班级；</li>
     *   <li>不能禁用自己、不能移除自己的管理员角色；</li>
     *   <li>不能禁用或降级「最后一个启用的系统管理员」。</li>
     * </ol>
     *
     * <p>角色 / 部门 / 禁用变更会在事务提交后注销该用户全部令牌，
     * 使权限与数据范围立即生效（登录快照缓存在 Redis 中）。</p>
     */
    @Transactional
    public UserDtos.UserVO updateBasic(Long id, UserDtos.BasicUpdateRequest req) {
        SysUser exist = userMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (req.getRealName() == null && req.getPhone() == null && req.getClassName() == null
                && req.getDeptId() == null && req.getStatus() == null && req.getRoleCodes() == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "没有需要更新的内容");
        }

        List<String> currentRoles = roleCodesOf(id);
        boolean roleChange = req.getRoleCodes() != null;
        List<String> targetRoles = roleChange ? normalizeRoles(req.getRoleCodes()) : currentRoles;

        boolean isStudent = targetRoles.contains(RoleCode.STUDENT);
        boolean isStaff = targetRoles.stream()
                .anyMatch(r -> RoleCode.REPAIRER.equals(r) || RoleCode.MANAGER.equals(r) || RoleCode.ADMIN.equals(r));

        // R1 至少一个角色
        if (targetRoles.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "至少保留一个角色");
        }
        // 角色必须存在且启用
        if (roleChange) {
            long valid = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .in(SysRole::getCode, targetRoles)
                    .eq(SysRole::getStatus, 1));
            if (valid != targetRoles.size()) {
                throw new BizException(ResultCode.PARAM_INVALID, "存在无效或已停用的角色");
            }
        }
        if (isStudent && isStaff) {
            throw new BizException(ResultCode.PARAM_INVALID, "学生与教职工角色不能同时选择");
        }

        // 目标终态：未提交的字段沿用原值
        String desiredClassName = req.getClassName() != null ? req.getClassName() : exist.getClassName();
        Long desiredDeptId = req.getDeptId() == null ? exist.getDeptId()
                : (req.getDeptId() == 0L ? null : req.getDeptId());
        Integer desiredStatus = req.getStatus() != null ? req.getStatus() : exist.getStatus();

        if (desiredStatus == null || (desiredStatus != 0 && desiredStatus != 1)) {
            throw new BizException(ResultCode.PARAM_INVALID, "状态只能是启用或禁用");
        }

        // R5 / R6 角色与班级、部门的一致性（仅在角色变更或显式提交对应字段时校验，
        // 避免「只改手机号」也被历史脏数据拦住）
        boolean deptSubmitted = req.getDeptId() != null;
        if (isStudent) {
            if ((roleChange || req.getClassName() != null) && !StringUtils.hasText(desiredClassName)) {
                throw new BizException(ResultCode.PARAM_INVALID, "学生必须填写班级");
            }
            desiredDeptId = null;
        } else if (isStaff) {
            if (desiredDeptId == null) {
                if (roleChange || deptSubmitted) {
                    throw new BizException(ResultCode.PARAM_INVALID, "教职工必须选择所属部门");
                }
            } else if (roleChange || deptSubmitted) {
                SysDept dept = deptMapper.selectById(desiredDeptId);
                if (dept == null || dept.getStatus() == null || dept.getStatus() != 1) {
                    throw new BizException(ResultCode.PARAM_INVALID, "所属部门不存在或已停用");
                }
            }
            desiredClassName = null;
        }

        // R2 不能禁用自己
        boolean self = id.equals(CurrentUserHolder.id());
        if (self && desiredStatus == 0) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "不能禁用当前登录账号");
        }
        // R3 不能移除自己的管理员角色
        if (self && currentRoles.contains(RoleCode.ADMIN) && !targetRoles.contains(RoleCode.ADMIN)) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "不能移除自己的系统管理员角色");
        }
        // R4 不能禁用或降级最后一个启用的系统管理员
        boolean wasActiveAdmin = currentRoles.contains(RoleCode.ADMIN) && Integer.valueOf(1).equals(exist.getStatus());
        boolean willBeActiveAdmin = targetRoles.contains(RoleCode.ADMIN) && Integer.valueOf(1).equals(desiredStatus);
        if (wasActiveAdmin && !willBeActiveAdmin && activeAdminCountExcluding(id) == 0) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "系统至少保留一名启用的系统管理员");
        }

        // 只写有变化的列，保证动态 SQL 的 SET 子句最小化
        UserDtos.BasicUpdateRequest patch = new UserDtos.BasicUpdateRequest();
        boolean changed = false;
        if (req.getRealName() != null && !req.getRealName().equals(exist.getRealName())) {
            patch.setRealName(req.getRealName().trim());
            changed = true;
        }
        if (req.getPhone() != null && !req.getPhone().equals(exist.getPhone())) {
            patch.setPhone(req.getPhone().trim());
            changed = true;
        }
        if (!java.util.Objects.equals(desiredClassName, exist.getClassName())) {
            patch.setClassName(desiredClassName == null ? "" : desiredClassName.trim());
            changed = true;
        }
        if (!java.util.Objects.equals(desiredDeptId, exist.getDeptId())) {
            patch.setDeptId(desiredDeptId == null ? 0L : desiredDeptId);
            changed = true;
        }
        if (!java.util.Objects.equals(desiredStatus, exist.getStatus())) {
            patch.setStatus(desiredStatus);
            changed = true;
        }
        patch.setExpectedUpdatedAt(req.getExpectedUpdatedAt());

        if (changed) {
            int rows = userMapper.updateBasic(id, patch);
            if (rows == 0) {
                if (req.getExpectedUpdatedAt() != null) {
                    throw BizException.conflict(ResultCode.STATUS_CHANGED, "数据已变更，请刷新后重试");
                }
                throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
            }
        }

        boolean rolesChanged = roleChange && !new java.util.HashSet<>(currentRoles).equals(new java.util.HashSet<>(targetRoles));
        if (rolesChanged) {
            assignRoles(id, targetRoles);
        }

        log.info("编辑用户 id={} operator={} 变更={} 角色={} 部门={} 状态={}",
                id, CurrentUserHolder.id(), changed, targetRoles, desiredDeptId, desiredStatus);

        // 涉及鉴权快照的变更，提交后注销该用户全部令牌
        boolean kick = rolesChanged
                || !java.util.Objects.equals(desiredDeptId, exist.getDeptId())
                || (Integer.valueOf(0).equals(desiredStatus) && !Integer.valueOf(0).equals(exist.getStatus()));
        if (kick) {
            logoutAfterCommit(id);
        }

        return detail(id);
    }

    /** 角色码规范化：去空、去重、转大写，保持稳定顺序。 */
    private List<String> normalizeRoles(List<String> roleCodes) {
        if (roleCodes == null) {
            return List.of();
        }
        return roleCodes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    /** 统计除指定用户外，仍处于启用状态的系统管理员数量。 */
    private long activeAdminCountExcluding(Long excludeId) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .ne(SysUser::getId, excludeId)
                .inSql(SysUser::getId,
                        "SELECT ur.user_id FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                                + "WHERE r.code = '" + RoleCode.ADMIN + "'"));
        return count == null ? 0 : count;
    }

    /** 事务提交后注销令牌；无事务上下文时立即执行。 */
    private void logoutAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tokenProvider.logoutAll(userId);
                }
            });
        } else {
            tokenProvider.logoutAll(userId);
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

    /**
     * 删除成员（逻辑删除）。
     *
     * <p>采用逻辑删除而非物理删除：工单、投诉、评价、操作日志均以 user_id 关联，
     * 物理删除会让历史记录失去主体。删除时同时为 username / student_no / staff_no
     * 追加 {@code #D<id>} 墓碑后缀，让唯一索引让位，后续可重新开通同学号 / 工号。</p>
     *
     * <p>以下三种情况拒绝删除：</p>
     * <ol>
     *   <li>删除当前登录账号（避免把自己锁在系统外）；</li>
     *   <li>删除「最后一个启用的系统管理员」（系统将无人可管理）；</li>
     *   <li>作为报修人或维修人仍有未完结工单（工单会失去责任人）。</li>
     * </ol>
     */
    public void delete(Long id) {
        deleteOne(id);
    }

    /**
     * 批量删除：逐个校验、逐个删除，<b>不做全有或全无</b>。
     *
     * <p>能删的删掉，不能删的连同原因一起返回，便于前端展示
     * 「哪些没删掉、为什么」，避免一条不合规导致整批失败。</p>
     */
    public UserDtos.DeleteResult deleteBatch(List<Long> ids) {
        List<Long> targets = ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
        int success = 0;
        List<UserDtos.DeleteFailure> failed = new ArrayList<>();
        for (Long id : targets) {
            try {
                deleteOne(id);
                success++;
            } catch (BizException e) {
                failed.add(new UserDtos.DeleteFailure(id, displayName(id), e.getMessage()));
            }
        }
        log.info("批量删除成员 operator={} 提交={} 成功={} 跳过={}",
                CurrentUserHolder.id(), targets.size(), success, failed.size());
        return new UserDtos.DeleteResult(success, failed);
    }

    /** 单条删除：校验 → 逻辑删除 → 注销令牌。 */
    private void deleteOne(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "成员不存在或已被删除");
        }
        if (id.equals(CurrentUserHolder.id())) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "不能删除当前登录账号");
        }
        boolean activeAdmin = Integer.valueOf(1).equals(user.getStatus())
                && roleCodesOf(id).contains(RoleCode.ADMIN);
        if (activeAdmin && activeAdminCountExcluding(id) == 0) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED, "系统至少保留一名启用的系统管理员");
        }
        int activeOrders = repairOrderMapper.countActiveByUser(id);
        if (activeOrders > 0) {
            throw new BizException(ResultCode.STATUS_NOT_ALLOWED,
                    "仍有 " + activeOrders + " 张未完结工单，请先转派或办结");
        }
        if (userMapper.logicalDeleteById(id) == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "成员不存在或已被删除");
        }
        tokenProvider.logoutAll(id);
        log.info("删除成员 id={} username={} operator={}", id, user.getUsername(), CurrentUserHolder.id());
    }

    /** 失败明细展示用：姓名（账号）；记录已不可读时退化为 id。 */
    private String displayName(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            return "#" + id;
        }
        return StringUtils.hasText(u.getRealName())
                ? u.getRealName() + "（" + u.getUsername() + "）"
                : u.getUsername();
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
