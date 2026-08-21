package com.flexcore.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to update a user's basic info (admin)")
public class UpdateUserRequest {

    @NotBlank(message = "{validation.full-name.required}")
    @Size(max = 150, message = "{validation.full-name.size}")
    @Schema(description = "User full name", example = "Ahmed Hassan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fullName;

    @Size(max = 30)
    @Schema(description = "Phone number", example = "+201001234567")
    private String phoneNumber;
}
