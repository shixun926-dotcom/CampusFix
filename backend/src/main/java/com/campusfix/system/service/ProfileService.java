package com.campusfix.system.service;

import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.common.security.LoginUser;
import com.campusfix.file.FileStorageService;
import com.campusfix.system.dto.UserDtos;
import com.campusfix.system.entity.SysDept;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysDeptMapper;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 个人中心服务：维修工自助维护个人资料。
 *
 * <p>字段权限（本次需求裁决）：
 * <ul>
 *   <li>姓名 / 工号：管理员导入时生成，<b>不可编辑</b>；</li>
 *   <li>所属区域：由管理员在用户管理维护，<b>不可编辑</b>；</li>
 *   <li>手机号：<b>唯一允许用户自助修改</b>的字段；</li>
 *   <li>头像：使用系统统一默认值，用户可主动更换（非强制）；未更换时
 *       {@code hasAvatar=false}，前端据此展示「尽快更新头像」提醒。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final FileStorageService fileStorageService;

    /** 读取当前登录维修工的个人资料。 */
    public UserDtos.ProfileVO profile() {
        LoginUser me = CurrentUserHolder.get();
        SysUser user = userMapper.selectById(me.getId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toProfileVO(user);
    }

    /** 保存个人资料：仅更新手机号（+ 可选更换头像），其余字段防御式忽略。 */
    @Transactional
    public UserDtos.ProfileVO update(UserDtos.ProfileUpdateRequest req) {
        LoginUser me = CurrentUserHolder.get();
        SysUser exist = userMapper.selectById(me.getId());
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }

        String phone = StringUtils.hasText(req.phone()) ? req.phone().trim() : null;
        // 头像可选：传了才更新，不传保持原值
        String avatar = StringUtils.hasText(req.avatar()) ? req.avatar().trim() : null;

        SysUser update = new SysUser();
        update.setId(me.getId());
        update.setPhone(phone);
        if (avatar != null) {
            update.setAvatar(avatar);
        }
        userMapper.updateById(update);

        SysUser fresh = userMapper.selectById(me.getId());
        return toProfileVO(fresh);
    }

    private UserDtos.ProfileVO toProfileVO(SysUser u) {
        UserDtos.ProfileVO vo = new UserDtos.ProfileVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setRealName(u.getRealName());
        vo.setStaffNo(u.getStaffNo());
        vo.setPhone(u.getPhone());
        vo.setAvatar(u.getAvatar());
        vo.setAvatarUrl(fileStorageService.getUrl(u.getAvatar()));
        boolean hasAvatar = StringUtils.hasText(u.getAvatar());
        vo.setHasAvatar(hasAvatar);
        vo.setAvatarDeadline(u.getAvatarDeadline());
        // 逾期判定：未更换头像 且 已过截止时间
        boolean overdue = !hasAvatar && u.getAvatarDeadline() != null
                && u.getAvatarDeadline().isBefore(java.time.LocalDateTime.now());
        vo.setAvatarOverdue(overdue);
        vo.setDeptId(u.getDeptId());
        if (u.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(u.getDeptId());
            vo.setDeptName(dept == null ? null : dept.getName());
        }
        return vo;
    }
}
