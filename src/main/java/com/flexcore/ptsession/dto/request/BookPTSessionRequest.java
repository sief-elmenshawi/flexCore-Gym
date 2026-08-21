package com.flexcore.ptsession.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Request to book a personal training session")
public class BookPTSessionRequest {

    @NotNull(message = "{validation.trainer-id.required}")
    @Schema(description = "Trainer user id", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long trainerId;

    @NotNull(message = "{validation.session-time.required}")
    @Future(message = "{validation.session-time.future}")
    @Schema(description = "Session start time", example = "2026-09-02T18:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime scheduledAt;

    @Min(value = 30, message = "{validation.session-minutes.min}")
    @Max(value = 120, message = "{validation.session-minutes.max}")
    @Schema(description = "Session duration in minutes", example = "60")
    private int durationMinutes = 60;
}
