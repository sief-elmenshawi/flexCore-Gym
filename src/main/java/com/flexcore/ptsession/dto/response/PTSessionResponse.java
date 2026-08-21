package com.flexcore.ptsession.dto.response;

import com.flexcore.ptsession.enums.PTSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Personal training session details")
public class PTSessionResponse {

    @Schema(description = "Session id", example = "9")
    private Long id;

    @Schema(description = "Member user id", example = "5")
    private Long memberId;

    @Schema(description = "Member full name", example = "Sara Ali")
    private String memberName;

    @Schema(description = "Trainer user id", example = "2")
    private Long trainerId;

    @Schema(description = "Trainer full name", example = "Omar Khaled")
    private String trainerName;

    @Schema(description = "Session start time", example = "2026-09-02T18:00:00")
    private LocalDateTime scheduledAt;

    @Schema(description = "Session duration in minutes", example = "60")
    private int durationMinutes;

    @Schema(description = "Session status", example = "SCHEDULED")
    private PTSessionStatus status;
}
