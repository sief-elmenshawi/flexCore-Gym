package com.flexcore.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to authenticate and receive a JWT access token")
public class LoginRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Schema(description = "Registered email", example = "sara@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Schema(description = "Plain text password", example = "Str0ngP@ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
