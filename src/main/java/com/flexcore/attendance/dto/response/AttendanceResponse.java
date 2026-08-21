package com.flexcore.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Attendance check-in details")
public class AttendanceResponse {

    @Schema(description = "Attendance id", example = "12")
    private Long id;

    @Schema(description = "Member user id", example = "5")
    private Long userId;

    @Schema(description = "Member full name", example = "Sara Ali")
    private String userName;

    @Schema(description = "Subscription used for this visit", example = "7")
    private Long subscriptionId;

    @Schema(description = "Receptionist who checked the member in", example = "Mona Adel")
    private String checkedInByName;

    @Schema(description = "Check-in time", example = "2026-08-21T09:00:00")
    private LocalDateTime checkInAt;
}
