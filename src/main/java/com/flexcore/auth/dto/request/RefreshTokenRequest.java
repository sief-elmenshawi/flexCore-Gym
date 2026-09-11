package com.flexcore.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request to obtain a fresh access token from a refresh token")
public class RefreshTokenRequest {

    @NotBlank(message = "{validation.refresh-token.required}")
    @Schema(description = "Opaque refresh token issued at login/registration", example = "abc...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}