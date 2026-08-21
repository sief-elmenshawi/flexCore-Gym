package com.flexcore.role.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

@Getter
@Builder
@Schema(description = "Role details with granted permissions")
public class RoleResponse {

    @Schema(description = "Role id", example = "1")
    private Long id;

    @Schema(description = "Role name", example = "MEMBER")
    private String name;

    @Schema(description = "Permissions granted to this role")
    private Set<PermissionResponse> permissions;
}
