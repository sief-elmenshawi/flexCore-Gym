package com.flexcore.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to create a family group for a family plan")
public class CreateFamilyGroupRequest {

    @NotNull(message = "{validation.plan-id.required}")
    @Schema(description = "Family plan id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long planId;
}
