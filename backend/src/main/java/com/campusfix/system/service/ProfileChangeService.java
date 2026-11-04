package com.campusfix.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campusfix.common.enums.ProfileChangeStatus;
import com.campusfix.common.enums.RoleCode;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.PageResult;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.common.security.LoginUser;
import com.campusfix.message.service.NotificationService;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.entity.ProfileChangeRequest;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.ProfileChangeRequestMapper;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 维修工信息更改申请：提交 → 后勤管理员审核 → 通过（落地）/ 打回（附原因 + 通知申请人）。
 *
 * <p>支持字段：real_name（姓名）、staff_no（工号）、dept（所属区域）。
 * 这些字段由管理员导入时生成、本人不可自助修改，出错时走本流程更正，
 * 保证「导入数据错误」能被追溯、被审核地修正，而不是静默直改。</p>
 *
 * <p>审核角色裁决：<b>后勤管理员（MANAGER）</b>，而非系统管理员。
 * 理由：维修工隶属维修部门，其身份信息属「组织人事」范畴，由后勤按部门分片管理；
 * ADMIN 职责是系统级配置 / 权限 / 审计，不应陷入日常人事审核。MANAGER 仅能审核本部门维修工的申请。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileChangeService {

    /** 可申请更改的字段（本人不可自助修改的字段） */
    public static final Map<String, String> FIELDS = Map.of(
            "real_name", "姓名",
            "staff_no", "工号",
            "dept", "所属区域");

    private final ProfileChangeRequestMapper requestMapper;
    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final NotificationService notificationService;

    // ------------------------------------------------------------------ 提交

    /**
     * 维修工提交信息更改申请。同一字段若已有待审核的申请，不允许重复提交。
     */
    @Transactional
    public UserDtos.ProfileChangeVO submit(UserDtos.ProfileChangeSubmitRequest req) {
        LoginUser me = CurrentUserHolder.get();
        String fieldKey = req.fieldKey().trim();
        String fieldLabel = FIELDS.get(fieldKey);
        if (fieldLabel == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "不支持更改该字段");
        }

        SysUser user = userMapper.selectById(me.getId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 同一字段已有待审核申请则拦截，避免重复提交堆积
        Long open = requestMapper.selectCount(new LambdaQueryWrapper<ProfileChangeRequest>()
                .eq(ProfileChangeRequest::getApplicantId, me.getId())
                .eq(ProfileChangeRequest::getFieldKey, fieldKey)
                .eq(ProfileChangeRequest::getStatus, ProfileChangeStatus.PENDING.name()));
        if (open != null && open > 0) {
            throw new BizException(ResultCode.DUPLICATE, "该字段已有一条待审核的更改申请，请等待审核结果");
        }

        String newValue = req.newValue().trim();
        String oldValue = currentValue(user, fieldKey);

        ProfileChangeRequest r = new ProfileChangeRequest();
        r.setApplicantId(me.getId());
        r.setFieldKey(fieldKey);
        r.setFieldLabel(fieldLabel);
        r.setOldValue(oldValue);
        r.setNewValue(newValue);
        r.setReason(req.reason().trim());
        r.setStatus(ProfileChangeStatus.PENDING.name());
        requestMapper.insert(r);

        log.info("维修工 {} 提交「{}」更改申请 #{}({} -> {})", me.getId(), fieldLabel, r.getId(), oldValue, newValue);
        return toVO(r);
    }

    // ------------------------------------------------------------------ 审核

    /**
     * 后勤管理员审核。通过则把新值落地到 sys_user；打回则附原因并通知申请人。
     * MANAGER 只能审核本部门（含下级）维修工的申请。
     */
    @Transactional
    public UserDtos.ProfileChangeVO review(Long id, UserDtos.ProfileChangeReviewRequest req, LoginUser me) {
        ProfileChangeRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException(ResultCode.NOT_FOUND, "申请不存在");
        }
        if (ProfileChangeStatus.of(r.getStatus()).isClosed()) {
            throw new BizException(ResultCode.PARAM_INVALID, "该申请已审核完毕，不能重复处理");
        }

        ProfileChangeStatus target = ProfileChangeStatus.of(req.status());
        if (target == ProfileChangeStatus.PENDING) {
            throw new BizException(ResultCode.PARAM_INVALID, "审核结果只能是通过或打回");
        }

        SysUser applicant = userMapper.selectById(r.getApplicantId());
        if (applicant == null) {
            throw new BizException(ResultCode.NOT_FOUND, "申请人不存在");
        }

        // 部门分片：MANAGER 只能审核本部门（含下级）维修工
        requireDeptScope(applicant, me);

        if (target == ProfileChangeStatus.REJECTED) {
            if (!StringUtils.hasText(req.rejectReason())) {
                throw new BizException(ResultCode.PARAM_INVALID, "打回时必须填写原因");
            }
            r.setStatus(ProfileChangeStatus.REJECTED.name());
            r.setRejectReason(req.rejectReason().trim());
            r.setReviewerId(me.getId());
            r.setReviewedAt(LocalDateTime.now());
            requestMapper.updateById(r);

            notificationService.notify(r.getApplicantId(), NotificationService.TYPE_SYSTEM,
                    "信息更改申请被退回",
                    "你提交的「" + r.getFieldLabel() + "」更改申请已被退回，原因：" + r.getRejectReason()
                            + "。可修改后重新提交。", r.getId());
            log.info("申请 #{} 被 {} 打回，原因：{}", r.getId(), me.getId(), r.getRejectReason());
        } else {
            // 通过：落地新值
            applyChange(applicant, r);
            r.setStatus(ProfileChangeStatus.APPROVED.name());
            r.setReviewerId(me.getId());
            r.setReviewedAt(LocalDateTime.now());
            requestMapper.updateById(r);

            notificationService.notify(r.getApplicantId(), NotificationService.TYPE_SYSTEM,
                    "信息更改申请已通过",
                    "你提交的「" + r.getFieldLabel() + "」更改申请已通过审核，"
                            + r.getOldValue() + " → " + r.getNewValue() + "。", r.getId());
            log.info("申请 #{} 通过，{} 的「{}」已由 {} 改为 {}",
                    r.getId(), applicant.getId(), r.getFieldLabel(), r.getOldValue(), r.getNewValue());
        }

        return toVO(r);
    }

    /** 审核通过后把新值写入 sys_user。 */
    private void applyChange(SysUser user, ProfileChangeRequest r) {
        String key = r.getFieldKey();
        String newValue = r.getNewValue();
        SysUser update = new SysUser();
        update.setId(user.getId());
        switch (key) {
            case "real_name" -> update.setRealName(newValue);
            case "staff_no" -> {
                // 工号唯一性校验
                Long dup = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStaffNo, newValue).ne(SysUser::getId, user.getId()));
                if (dup != null && dup > 0) {
                    throw new BizException(ResultCode.DUPLICATE, "工号 " + newValue + " 已被占用");
                }
                update.setStaffNo(newValue);
            }
            case "dept" -> {
                SysDept dept = deptMapper.selectOne(new LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getName, newValue));
                if (dept == null) {
                    throw new BizException(ResultCode.PARAM_INVALID, "所属区域「" + newValue + "」不存在");
                }
                update.setDeptId(dept.getId());
            }
            default -> throw new BizException(ResultCode.PARAM_INVALID, "不支持更改该字段");
        }
        userMapper.updateById(update);
    }

    // ------------------------------------------------------------------ 查询

    /** 我的申请列表（维修工查看自己的申请记录）。 */
    public PageResult<UserDtos.ProfileChangeVO> myRequests(int page, int size) {
        LoginUser me = CurrentUserHolder.get();
        LambdaQueryWrapper<ProfileChangeRequest> w = new LambdaQueryWrapper<ProfileChangeRequest>()
                .eq(ProfileChangeRequest::getApplicantId, me.getId())
                .orderByDesc(ProfileChangeRequest::getCreatedAt);
        return page(w, page, size);
    }

    /** 审核列表（后勤管理员按部门分片，ADMIN 全量）。 */
    public PageResult<UserDtos.ProfileChangeVO> reviewList(String status, int page, int size, LoginUser me) {
        LambdaQueryWrapper<ProfileChangeRequest> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            w.eq(ProfileChangeRequest::getStatus, status);
        }
        if (!me.hasRole(RoleCode.ADMIN)) {
            // MANAGER：只看本部门（含下级）维修工的申请
            Set<Long> depts = me.getDeptId() == null ? Set.of() : deptScope(me.getDeptId());
            if (depts.isEmpty()) {
                w.eq(ProfileChangeRequest::getApplicantId, me.getId());
            } else {
                w.inSql(ProfileChangeRequest::getApplicantId,
                        "SELECT id FROM sys_user WHERE dept_id IN ("
                                + depts.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")");
            }
        }
        w.orderByAsc(ProfileChangeRequest::getStatus).orderByDesc(ProfileChangeRequest::getCreatedAt);
        return page(w, page, size);
    }

    private PageResult<UserDtos.ProfileChangeVO> page(LambdaQueryWrapper<ProfileChangeRequest> w, int page, int size) {
        IPage<ProfileChangeRequest> p = requestMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), w);
        List<UserDtos.ProfileChangeVO> list = p.getRecords().isEmpty() ? List.of()
                : p.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(list, p.getTotal(), p.getCurrent(), p.getSize());
    }

    private Set<Long> deptScope(Long deptId) {
        // 复用 DeptService 逻辑
        Set<Long> result = new java.util.HashSet<>();
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

    private void requireDeptScope(SysUser applicant, LoginUser me) {
        if (me.hasRole(RoleCode.ADMIN)) {
            return;
        }
        if (applicant.getDeptId() == null || me.getDeptId() == null
                || !deptScope(me.getDeptId()).contains(applicant.getDeptId())) {
            throw new BizException(ResultCode.DATA_FORBIDDEN, "无权审核该维修工的申请");
        }
    }

    private String currentValue(SysUser user, String fieldKey) {
        return switch (fieldKey) {
            case "real_name" -> user.getRealName();
            case "staff_no" -> user.getStaffNo();
            case "dept" -> {
                if (user.getDeptId() == null) {
                    yield null;
                }
                SysDept dept = deptMapper.selectById(user.getDeptId());
                yield dept == null ? null : dept.getName();
            }
            default -> null;
        };
    }

    private UserDtos.ProfileChangeVO toVO(ProfileChangeRequest r) {
        UserDtos.ProfileChangeVO vo = new UserDtos.ProfileChangeVO();
        vo.setId(r.getId());
        vo.setApplicantId(r.getApplicantId());
        vo.setFieldKey(r.getFieldKey());
        vo.setFieldLabel(r.getFieldLabel());
        vo.setOldValue(r.getOldValue());
        vo.setNewValue(r.getNewValue());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setStatusLabel(ProfileChangeStatus.of(r.getStatus()).getLabel());
        vo.setReviewerId(r.getReviewerId());
        vo.setRejectReason(r.getRejectReason());
        vo.setReviewedAt(r.getReviewedAt());
        vo.setCreatedAt(r.getCreatedAt());

        Map<Long, String> names = namesOf(r.getApplicantId(), r.getReviewerId());
        vo.setApplicantName(names.get(r.getApplicantId()));
        vo.setReviewerName(names.get(r.getReviewerId()));

        SysUser applicant = userMapper.selectById(r.getApplicantId());
        vo.setApplicantStaffNo(applicant == null ? null : applicant.getStaffNo());
        return vo;
    }

    private Map<Long, String> namesOf(Long... ids) {
        List<Long> valid = java.util.Arrays.stream(ids).filter(Objects::nonNull).distinct().toList();
        if (valid.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(valid).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName, (a, b) -> a));
    }
}
