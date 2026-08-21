package com.flexcore.core.security;

public record CustomUserPrincipal(
        Long id,
        String email,
        String roleName
) {
}
