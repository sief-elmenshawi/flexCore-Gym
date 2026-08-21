package com.flexcore.role.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Schema(description = "Request to create a role with a set of permissions")
public class CreateRoleRequest {

    @NotBlank(message = "{validation.role-name.required}")
    @Size(max = 50, message = "{validation.role-name.size}")
    @Schema(description = "Unique role name", example = "MANAGER", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Permission codes to grant to this role", example = "[\"BOOK_CLASS\", \"VIEW_REPORTS\"]")
    private Set<String> permissionCodes;
}
