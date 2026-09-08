package com.flexcore.auth;

import com.flexcore.auth.dto.request.LoginRequest;
import com.flexcore.auth.dto.request.RegisterRequest;
import com.flexcore.auth.dto.response.AuthResponse;
import com.flexcore.auth.service.impl.AuthServiceImpl;
import com.flexcore.core.exception.DuplicateResourceException;
import com.flexcore.core.security.JwtTokenProvider;
import com.flexcore.core.security.LoginRateLimiter;
import com.flexcore.role.entity.Role;
import com.flexcore.role.repository.RoleRepository;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private LoginRateLimiter loginRateLimiter;

    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(7L)
                .fullName("Ahmed Test")
                .email("ahmed@test.com")
                .passwordHash("$2a$hash")
                .active(true)
                .role(Role.builder().id(2L).name("MEMBER").build())
                .build();
    }

    private RegisterRequest registerRequest(String email) {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Ahmed Test");
        request.setEmail(email);
        request.setPassword("Str0ngP@ss");
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void register_createsMemberWithHashedPasswordAndNormalizedEmail() {
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(roleRepository.findByName("MEMBER"))
                .thenReturn(Optional.of(Role.builder().id(2L).name("MEMBER").build()));
        when(passwordEncoder.encode("Str0ngP@ss")).thenReturn("$2a$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(jwtTokenProvider.generateAccessToken(any(), any(), any(), any())).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest(" New@Test.COM "));

        assertEquals("jwt-token", response.getAccessToken());
        verify(loginRateLimiter, never()).recordFailure(any());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new@test.com", captor.getValue().getEmail());
        assertEquals("$2a$hashed", captor.getValue().getPasswordHash());
        assertTrue(captor.getValue().isActive());
        assertEquals("MEMBER", captor.getValue().getRole().getName());
    }

    @Test
    void register_duplicateEmailThrows() {
        when(userRepository.existsByEmail("taken@test.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> authService.register(registerRequest("Taken@Test.COM")));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_successClearsFailureCounter() {
        when(userRepository.findByEmail("ahmed@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", "$2a$hash")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(7L, "ahmed@test.com", "MEMBER", Set.of())).thenReturn("jwt");

        AuthResponse response = authService.login(loginRequest(" Ahmed@Test.COM ", "Passw0rd!"));

        assertEquals("jwt", response.getAccessToken());
        assertEquals(7L, response.getUserId());
        verify(loginRateLimiter).checkNotBlocked("ahmed@test.com");
        verify(loginRateLimiter).onSuccess("ahmed@test.com");
        verify(loginRateLimiter, never()).recordFailure(any());
    }

    @Test
    void login_wrongPasswordRecordsFailure() {
        when(userRepository.findByEmail("ahmed@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "$2a$hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest("ahmed@test.com", "bad")));

        verify(loginRateLimiter).recordFailure("ahmed@test.com");
        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any(), any());
    }

    @Test
    void login_unknownEmailRecordsFailure() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest("ghost@test.com", "whatever")));

        verify(loginRateLimiter).recordFailure("ghost@test.com");
    }

    @Test
    void login_deactivatedAccountRecordsFailure() {
        user.setActive(false);
        when(userRepository.findByEmail("ahmed@test.com")).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class,
                () -> authService.login(loginRequest("ahmed@test.com", "Passw0rd!")));

        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginRateLimiter).recordFailure("ahmed@test.com");
    }
}
