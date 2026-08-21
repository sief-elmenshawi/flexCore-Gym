package com.flexcore.role.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Schema(description = "Request to update a role's name and permissions (full replace)")
public class UpdateRoleRequest {

    @NotBlank(message = "{validation.role-name.required}")
    @Size(max = 50, message = "{validation.role-name.size}")
    @Schema(description = "Unique role name", example = "MANAGER", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Permission codes granted to this role (replaces existing)", example = "[\"BOOK_CLASS\"]")
    private Set<String> permissionCodes;
}
