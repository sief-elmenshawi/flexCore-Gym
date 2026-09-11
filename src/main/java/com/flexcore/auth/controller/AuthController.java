package com.flexcore.auth.controller;

import com.flexcore.auth.dto.request.LoginRequest;
import com.flexcore.auth.dto.request.RefreshTokenRequest;
import com.flexcore.auth.dto.request.RegisterRequest;
import com.flexcore.auth.dto.response.AuthResponse;
import com.flexcore.auth.service.AuthService;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration and login endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @SecurityRequirements
    @Observed(name = "http.register", contextualName = "POST /api/v1/auth/register")
    @Operation(summary = "Register a new member account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created and access token returned"),
            @ApiResponse(responseCode = "409", description = "Email already registered")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Observed(name = "http.login", contextualName = "POST /api/v1/auth/login")
    @Operation(summary = "Authenticate and receive a JWT access token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or deactivated account")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Observed(name = "http.refreshToken", contextualName = "POST /api/v1/auth/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token (token rotation)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair returned"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired or revoked refresh token")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Observed(name = "http.logout", contextualName = "POST /api/v1/auth/logout")
    @Operation(summary = "Revoke a refresh token (server-side session invalidation)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Refresh token revoked")
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
