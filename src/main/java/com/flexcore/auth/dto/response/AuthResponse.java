package com.flexcore.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Authentication result with JWT access token")
public class AuthResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;

    @Schema(description = "Authenticated user id", example = "1")
    private Long userId;

    @Schema(description = "Authenticated user email", example = "sara@example.com")
    private String email;

    @Schema(description = "Authenticated user role", example = "MEMBER")
    private String roleName;
}
