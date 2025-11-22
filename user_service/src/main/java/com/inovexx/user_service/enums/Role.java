package com.inovexx.user_service.enums;

import org.springframework.security.core.GrantedAuthority;

/**
 * Перечисление Role (тип пользователя)
 */
public enum Role implements GrantedAuthority {
    ROLE_USER,
    ROLE_MANAGER,
    ROLE_ADMIN;

    @Override
    public String getAuthority() {
        return name();
    }
}