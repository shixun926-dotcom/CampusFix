package com.campusfix.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusfix.common.security.LoginUser;
import com.campusfix.system.entity.SysUser;
import com.campusfix.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 数据库用户加载器。
 *
 * <p>本项目登录由 {@link AuthService} 手工校验密码，并不走 DaoAuthenticationProvider；
 * 但必须提供该 Bean，否则 Spring Boot 的 UserDetailsServiceAutoConfiguration 会
 * 自动生成一个内存用户并打印 "Using generated security password"——那等于系统里
 * 凭空多出一个可用的后门账号。</p>
 */
@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final AuthService authService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new UsernameNotFoundException("账号已禁用：" + username);
        }
        LoginUser loginUser = authService.buildLoginUser(user);
        // 返回带密码哈希的标准 UserDetails：本类仅为占位与兼容，
        // 若将来改用 AuthenticationManager，密码校验可直接复用该哈希。
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(loginUser.getAuthorities())
                .disabled(user.getStatus() != null && user.getStatus() == 0)
                .build();
    }
}
