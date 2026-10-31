package com.campusfix.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.auth.dto.AuthDtos;
import com.campusfix.auth.dto.AuthDtos.ChangePasswordRequest;
import com.campusfix.auth.dto.AuthDtos.LoginRequest;
import com.campusfix.auth.dto.AuthDtos.LoginResponse;
import com.campusfix.auth.dto.AuthDtos.UserProfile;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.CurrentUserHolder;
import com.campusfix.common.security.JwtProperties;
import com.campusfix.common.security.JwtTokenProvider;
import com.campusfix.common.security.LoginUser;
import com.campusfix.common.security.TokenStore;
import com.campusfix.system.entity.SysLoginLog;
import com.campusfix.system.entity.SysPermission;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysLoginLogMapper;
import com.campusfix.system.mapper.SysPermissionMapper;
import com.campusfix.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 认证服务：登录、刷新、注销、当前用户、改密。
 *
 * <p>登录失败 5 次锁定 15 分钟（Redis 计数）。
 * 账号由管理员按学号/工号批量注入，不开放注册。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String KEY_FAIL = "campusfix:login:fail:";
    private static final int MAX_FAIL = 5;
    private static final Duration LOCK_TTL = Duration.ofMinutes(15);

    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenStore tokenStore;

    @Transactional
    public LoginResponse login(LoginRequest req, HttpServletRequest httpRequest) {
        String username = req.username().trim();
        String failKey = KEY_FAIL + username;

        String fails = tokenStore.get(failKey);
        if (fails != null && Long.parseLong(fails) >= MAX_FAIL) {
            writeLoginLog(null, username, httpRequest, 0, "连续失败锁定");
            throw new BizException(ResultCode.ACCOUNT_LOCKED);
        }

        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            tokenStore.increment(failKey, LOCK_TTL.getSeconds());
            writeLoginLog(user == null ? null : user.getId(), username, httpRequest, 0, "账号或密码错误");
            throw new BizException(ResultCode.LOGIN_FAILED);
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            writeLoginLog(user.getId(), username, httpRequest, 0, "账号已禁用");
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        tokenStore.delete(failKey);

        LoginUser loginUser = buildLoginUser(user);
        String accessToken = tokenProvider.createAccessToken(loginUser);
        String refreshToken = tokenProvider.createRefreshToken(user.getId());

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);

        writeLoginLog(user.getId(), username, httpRequest, 1, "登录成功");

        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTtlSeconds(),
                loginUser.getId(),
                loginUser.getUsername(),
                loginUser.getRealName(),
                loginUser.getRoles(),
                loginUser.getPermissions(),
                loginUser.getCreditScore(),
                user.getPwdResetFlag() != null && user.getPwdResetFlag() == 1);
    }

    public LoginResponse refresh(String refreshToken) {
        Long userId = tokenProvider.parseRefresh(refreshToken);
        SysUser user = userMapper.selectById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        LoginUser loginUser = buildLoginUser(user);
        String accessToken = tokenProvider.createAccessToken(loginUser);
        return new LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTtlSeconds(),
                loginUser.getId(),
                loginUser.getUsername(),
                loginUser.getRealName(),
                loginUser.getRoles(),
                loginUser.getPermissions(),
                loginUser.getCreditScore(),
                false);
    }

    public void logout() {
        LoginUser user = CurrentUserHolder.get();
        tokenProvider.logout(user.getJti(), user.getId());
    }

    public UserProfile profile() {
        LoginUser user = CurrentUserHolder.get();
        List<SysPermission> permissions = permissionMapper.selectByUserId(user.getId());
        List<AuthDtos.MenuNode> menus = permissions.stream()
                .filter(p -> "MENU".equals(p.getType()) || "API".equals(p.getType()))
                .map(p -> new AuthDtos.MenuNode(p.getId(), p.getParentId(), p.getCode(),
                        p.getName(), p.getType(), p.getPath(), p.getIcon()))
                .toList();
        return new UserProfile(user.getId(), user.getUsername(), user.getRealName(), user.getDeptId(),
                user.getCreditScore(), user.getRoles(), user.getPermissions(), menus);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        LoginUser current = CurrentUserHolder.get();
        SysUser user = userMapper.selectById(current.getId());
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!passwordEncoder.matches(req.oldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.OLD_PASSWORD_ERROR);
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(req.newPassword()));
        update.setPwdResetFlag(0);
        userMapper.updateById(update);
        // 改密后所有设备重新登录
        tokenProvider.logoutAll(user.getId());
    }

    /** 组装登录快照：角色 + 权限码。 */
    public LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName());
        loginUser.setDeptId(user.getDeptId());
        loginUser.setCreditScore(user.getCreditScore());
        loginUser.setRoles(new LinkedHashSet<>(userMapper.selectRoleCodes(user.getId())));
        List<String> codes = userMapper.selectPermissionCodes(user.getId());
        Set<String> permissions = new LinkedHashSet<>(codes);
        // 管理员默认拥有全部权限
        if (loginUser.getRoles().contains(com.campusfix.common.enums.RoleCode.ADMIN)) {
            permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                            .eq(SysPermission::getStatus, 1))
                    .forEach(p -> permissions.add(p.getCode()));
        }
        loginUser.setPermissions(permissions);
        return loginUser;
    }

    private void writeLoginLog(Long userId, String username, HttpServletRequest request, int status, String msg) {
        try {
            SysLoginLog log = new SysLoginLog();
            log.setUserId(userId);
            log.setUsername(username);
            log.setIp(clientIp(request));
            log.setUserAgent(request.getHeader("User-Agent"));
            log.setStatus(status);
            log.setMsg(msg);
            log.setCreatedAt(LocalDateTime.now());
            loginLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("写登录日志失败", e);
        }
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
