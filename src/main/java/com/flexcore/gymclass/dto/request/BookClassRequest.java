package com.flexcore.gymclass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to book a seat in a gym class")
public class BookClassRequest {

    @NotNull(message = "{validation.class-id.required}")
    @Schema(description = "Gym class id to book", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long classId;
}
