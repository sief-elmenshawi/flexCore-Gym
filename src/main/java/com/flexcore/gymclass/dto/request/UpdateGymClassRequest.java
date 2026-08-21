package com.flexcore.gymclass.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to update a gym class")
public class UpdateGymClassRequest {

    @NotBlank(message = "{validation.class-name.required}")
    @Size(max = 100, message = "{validation.class-name.size}")
    @Schema(description = "Class name", example = "Morning Yoga", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "{validation.trainer-id.required}")
    @Schema(description = "Trainer user id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long trainerId;

    @NotNull(message = "{validation.capacity.required}")
    @Positive(message = "{validation.capacity.positive}")
    @Schema(description = "Maximum number of bookings (cannot be lower than current bookings)", example = "25", requiredMode = Schema.RequiredMode.REQUIRED)
    private int capacity;

    @NotNull(message = "{validation.start-time.required}")
    @Future(message = "{validation.start-time.future}")
    @Schema(description = "Class start time", example = "2026-09-01T08:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startsAt;

    @NotNull(message = "{validation.duration.required}")
    @Min(value = 15, message = "{validation.class-duration.min}")
    @Schema(description = "Class duration in minutes", example = "60", requiredMode = Schema.RequiredMode.REQUIRED)
    private int durationMinutes;
}
