package com.flexcore.attendance.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to check a member in at the front desk")
public class CheckInRequest {

    @NotNull(message = "{validation.user-id.required}")
    @Schema(description = "Member to check in", example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
}
