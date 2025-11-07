package com.inovexx.auth_service.security;

import com.inovexx.auth_service.entity.UserAuth;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Класс для сбора информации об авторизованном пользователе
 */


@Getter
public class SecurityUser implements UserDetails {

    private final String username;
    private final String password;
    private final boolean enabled;
    private final List<SimpleGrantedAuthority> authorities;

    public SecurityUser(UserAuth user) {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.enabled = true;
        this.authorities = List.of(new SimpleGrantedAuthority(user.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
        return enabled;
    }
}