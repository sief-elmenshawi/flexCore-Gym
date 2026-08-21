package com.flexcore.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to add a member to a family group")
public class AddFamilyMemberRequest {

    @NotNull(message = "{validation.user-id.required}")
    @Schema(description = "User to add to the family group", example = "6", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
}
