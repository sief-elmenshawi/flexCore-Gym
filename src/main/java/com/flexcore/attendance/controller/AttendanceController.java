package com.flexcore.attendance.controller;

import com.flexcore.attendance.dto.request.CheckInRequest;
import com.flexcore.attendance.dto.response.AttendanceResponse;
import com.flexcore.attendance.service.AttendanceService;
import com.flexcore.core.dto.response.PagedResponse;
import com.flexcore.core.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Attendance", description = "Front-desk check-in and attendance history")
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    @PreAuthorize("hasAuthority('CHECK_IN_MEMBER')")
    @Operation(summary = "Check a member in at the front desk",
            description = "Requires CHECK_IN_MEMBER permission. The member must have an active subscription.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Member checked in"),
            @ApiResponse(responseCode = "400", description = "Member has no active subscription or is deactivated"),
            @ApiResponse(responseCode = "404", description = "Member not found")
    })
    public ResponseEntity<AttendanceResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attendanceService.checkIn(request, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping
    @Operation(summary = "Attendance history",
            description = "Staff with CHECK_IN_MEMBER can query any user via ?userId=. Members see their own history.")
    public ResponseEntity<PagedResponse<AttendanceResponse>> history(
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 10) Pageable pageable) {

        Long targetUserId = userId != null && SecurityUtils.hasAuthority("CHECK_IN_MEMBER")
                ? userId
                : SecurityUtils.getCurrentUserId();
        return PagedResponse.ok(attendanceService.getMemberHistory(targetUserId, pageable));
    }
}
