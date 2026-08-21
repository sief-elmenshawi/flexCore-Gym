package com.flexcore.role.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Permission details")
public class PermissionResponse {

    @Schema(description = "Permission id", example = "3")
    private Long id;

    @Schema(description = "Permission code", example = "BOOK_CLASS")
    private String code;

    @Schema(description = "Permission description", example = "Member books a class")
    private String description;
}
