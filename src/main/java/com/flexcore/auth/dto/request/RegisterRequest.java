package com.flexcore.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to register a new member account")
public class RegisterRequest {

    @NotBlank(message = "{validation.full-name.required}")
    @Size(max = 150, message = "{validation.full-name.size}")
    @Schema(description = "Full name", example = "Sara Ali", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    @Size(max = 150)
    @Schema(description = "Email address", example = "sara@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, max = 72, message = "{validation.password.size}")
    @Schema(description = "Plain text password", example = "Str0ngP@ss", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Size(max = 30)
    @Schema(description = "Phone number", example = "+201001234567")
    private String phoneNumber;
}
