package com.flexcore.gymclass.dto.response;

import com.flexcore.gymclass.enums.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Class booking details")
public class ClassBookingResponse {

    @Schema(description = "Booking id", example = "10")
    private Long id;

    @Schema(description = "Member user id", example = "5")
    private Long userId;

    @Schema(description = "Member full name", example = "Sara Ali")
    private String userName;

    @Schema(description = "Booked class id", example = "1")
    private Long classId;

    @Schema(description = "Class name", example = "Morning Yoga")
    private String className;

    @Schema(description = "Trainer full name", example = "Omar Khaled")
    private String trainerName;

    @Schema(description = "Class start time", example = "2026-09-01T08:00:00")
    private LocalDateTime startsAt;

    @Schema(description = "Booking status", example = "CONFIRMED")
    private BookingStatus status;

    @Schema(description = "When the booking was made", example = "2026-08-21T12:00:00")
    private LocalDateTime bookedAt;
}
