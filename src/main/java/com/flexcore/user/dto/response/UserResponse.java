package com.flexcore.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "User details")
public class UserResponse {

    @Schema(description = "User id", example = "1")
    private Long id;

    @Schema(description = "User full name", example = "Ahmed Hassan")
    private String fullName;

    @Schema(description = "User email", example = "ahmed@flexcore.com")
    private String email;

    @Schema(description = "Phone number", example = "+201001234567")
    private String phoneNumber;

    @Schema(description = "Role name", example = "MEMBER")
    private String roleName;

    @Schema(description = "Whether the account is active", example = "true")
    private boolean active;

    @Schema(description = "Account creation date", example = "2026-08-21T10:15:30")
    private LocalDateTime createdDate;
}
