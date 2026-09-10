package com.flexcore.gymclass.controller;

import com.flexcore.core.dto.response.PagedResponse;
import com.flexcore.gymclass.dto.request.CreateGymClassRequest;
import com.flexcore.gymclass.dto.request.UpdateGymClassRequest;
import com.flexcore.gymclass.dto.response.GymClassResponse;
import com.flexcore.gymclass.service.GymClassService;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
@Tag(name = "Gym Classes", description = "Class scheduling and browsing")
public class GymClassController {

    private final GymClassService gymClassService;

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_OWN_SCHEDULE')")
    @Observed(name = "http.createClass", contextualName = "POST /api/v1/classes")
    @Operation(summary = "Schedule a new class", description = "Requires MANAGE_OWN_SCHEDULE permission")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Class scheduled"),
            @ApiResponse(responseCode = "404", description = "Trainer not found"),
            @ApiResponse(responseCode = "400", description = "User is not a trainer")
    })
    public ResponseEntity<GymClassResponse> create(@Valid @RequestBody CreateGymClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gymClassService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_OWN_SCHEDULE')")
    @Observed(name = "http.updateClass", contextualName = "PUT /api/v1/classes/{id}")
    @Operation(summary = "Update a class", description = "Requires MANAGE_OWN_SCHEDULE permission")
    public ResponseEntity<GymClassResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateGymClassRequest request) {
        return ResponseEntity.ok(gymClassService.update(id, request));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Get class details by id")
    @ApiResponse(responseCode = "404", description = "Class not found")
    public ResponseEntity<GymClassResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(gymClassService.getById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_OWN_SCHEDULE')")
    @Observed(name = "http.deleteClass", contextualName = "DELETE /api/v1/classes/{id}")
    @Operation(summary = "Delete a class with no bookings", description = "Requires MANAGE_OWN_SCHEDULE permission")
    @ApiResponse(responseCode = "204", description = "Class deleted")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        gymClassService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/upcoming")
    @SecurityRequirements
    @Operation(summary = "Search classes with filters and pagination",
            description = "Public endpoint. Filter by name, trainer, or date range. Pages are 1-indexed.")
    public ResponseEntity<PagedResponse<GymClassResponse>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long trainerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 10, sort = "startsAt", direction = Sort.Direction.ASC) Pageable pageable) {

        return PagedResponse.ok(gymClassService.search(name, trainerId, from, to, pageable));
    }
}
