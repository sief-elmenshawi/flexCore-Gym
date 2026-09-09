package com.flexcore.auth.service.impl;

import com.flexcore.auth.dto.request.LoginRequest;
import com.flexcore.auth.dto.request.RegisterRequest;
import com.flexcore.auth.dto.response.AuthResponse;
import com.flexcore.auth.service.AuthService;
import com.flexcore.core.exception.DuplicateResourceException;
import com.flexcore.core.security.JwtTokenProvider;
import com.flexcore.core.security.LoginRateLimiter;
import com.flexcore.role.entity.Role;
import com.flexcore.role.repository.RoleRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_MEMBER_ROLE = "MEMBER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginRateLimiter loginRateLimiter;

    @Override
    @Observed(name = "auth.register", contextualName = "User Registration")
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("error.auth.email-registered", email);
        }

        Role memberRole = roleRepository.findByName(DEFAULT_MEMBER_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_MEMBER_ROLE + " not found. Check seed migrations."));

        User user = User.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(memberRole)
                .active(true)
                .build();

        user = userRepository.save(user);
        log.info("Registered new member with id {} and email {}", user.getId(), email);

        return buildAuthResponse(user);
    }

    @Override
    @Observed(name = "auth.login", contextualName = "User Login")
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        loginRateLimiter.checkNotBlocked(email);

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

            if (!user.isActive()) {
                throw new BadCredentialsException("Account is deactivated");
            }

            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new BadCredentialsException("Invalid email or password");
            }

            loginRateLimiter.onSuccess(email);
            return buildAuthResponse(user);
        } catch (BadCredentialsException ex) {
            loginRateLimiter.recordFailure(email);
            throw ex;
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        Set<String> permissions = permissionsOf(user.getRole());
        String token = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().getName(), permissions);
        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .roleName(user.getRole().getName())
                .build();
    }

    private Set<String> permissionsOf(Role role) {
        if (role == null || role.getPermissions() == null) {
            return Set.of();
        }
        return role.getPermissions().stream()
                .map(permission -> permission.getCode())
                .collect(Collectors.toSet());
    }
}
