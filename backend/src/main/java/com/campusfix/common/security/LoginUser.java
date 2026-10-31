package com.campusfix.common.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 登录用户上下文，缓存于 Redis（key: auth:token:{jti}）。
 * 只保存鉴权必需字段，不保存密码。
 */
@Data
@NoArgsConstructor
public class LoginUser implements UserDetails {

    private Long id;
    private String username;
    private String realName;
    private Long deptId;
    private Integer creditScore;
    private Set<String> roles = new LinkedHashSet<>();
    private Set<String> permissions = new LinkedHashSet<>();
    /** 当前访问令牌 id，用于注销时精确失效 */
    private String jti;

    public LoginUser(Long id, String username, String realName) {
        this.id = id;
        this.username = username;
        this.realName = realName;
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (permissions == null || permissions.isEmpty()) {
            return Collections.emptyList();
        }
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String code) {
        return permissions != null && permissions.contains(code);
    }
}
