package com.flexcore.gymclass.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Gym class details")
public class GymClassResponse {

    @Schema(description = "Class id", example = "1")
    private Long id;

    @Schema(description = "Class name", example = "Morning Yoga")
    private String name;

    @Schema(description = "Trainer user id", example = "2")
    private Long trainerId;

    @Schema(description = "Trainer full name", example = "Omar Khaled")
    private String trainerName;

    @Schema(description = "Maximum capacity", example = "20")
    private int capacity;

    @Schema(description = "Current booked count", example = "12")
    private int bookedCount;

    @Schema(description = "Remaining seats", example = "8")
    private int availableSpots;

    @Schema(description = "Class start time", example = "2026-09-01T08:00:00")
    private LocalDateTime startsAt;

    @Schema(description = "Duration in minutes", example = "60")
    private int durationMinutes;
}
