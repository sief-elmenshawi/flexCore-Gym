package com.flexcore.gymclass.controller;

import com.flexcore.core.dto.response.PagedResponse;
import com.flexcore.core.security.SecurityUtils;
import com.flexcore.gymclass.dto.request.BookClassRequest;
import com.flexcore.gymclass.dto.response.ClassBookingResponse;
import com.flexcore.gymclass.service.ClassBookingService;
import com.flexcore.gymclass.service.impl.ClassBookingServiceImpl;
import io.micrometer.observation.annotation.Observed;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Class Bookings", description = "Book and cancel gym class seats")
public class ClassBookingController {

    private final ClassBookingService classBookingService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOK_CLASS')")
    @Observed(name = "http.book", contextualName = "POST /api/v1/bookings")
    @Operation(summary = "Book a seat in a class", description = "Requires BOOK_CLASS permission")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Seat booked"),
            @ApiResponse(responseCode = "400", description = "Class full, already started, or duplicate booking"),
            @ApiResponse(responseCode = "404", description = "Class not found")
    })
    public ResponseEntity<ClassBookingResponse> book(@Valid @RequestBody BookClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(classBookingService.book(request.getClassId(), SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/my")
    @Operation(summary = "List the current user's bookings with pagination")
    public ResponseEntity<PagedResponse<ClassBookingResponse>> myBookings(
            @PageableDefault(size = 10) Pageable pageable) {
        return PagedResponse.ok(classBookingService.getMyBookings(SecurityUtils.getCurrentUserId(), pageable));
    }

    @DeleteMapping("/{id}")
    @Observed(name = "http.cancelBooking", contextualName = "DELETE /api/v1/bookings/{id}")
    @Operation(summary = "Cancel a booking and free the seat")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Booking cancelled"),
            @ApiResponse(responseCode = "403", description = "Not your booking"),
            @ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        classBookingService.cancelBooking(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
