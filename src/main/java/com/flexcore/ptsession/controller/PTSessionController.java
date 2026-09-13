package com.flexcore.ptsession.controller;

import com.flexcore.core.dto.response.PagedResponse;
import com.flexcore.core.security.SecurityUtils;
import com.flexcore.ptsession.dto.request.BookPTSessionRequest;
import com.flexcore.ptsession.dto.response.PTSessionResponse;
import com.flexcore.ptsession.service.PTSessionService;
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
@RequestMapping("/api/v1/pt-sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "PT Sessions", description = "Personal training session booking")
public class PTSessionController {

    private final PTSessionService ptSessionService;

    @PostMapping
    @PreAuthorize("hasAuthority('BOOK_CLASS')")
    @Observed(name = "http.bookPTSession", contextualName = "POST /api/v1/pt-sessions")
    @Operation(summary = "Book a personal training session with a trainer",
            description = "Validates the trainer has no overlapping scheduled session.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session booked"),
            @ApiResponse(responseCode = "400", description = "Trainer busy at the requested time or not a trainer"),
            @ApiResponse(responseCode = "404", description = "Trainer not found")
    })
    public ResponseEntity<PTSessionResponse> book(@Valid @RequestBody BookPTSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ptSessionService.book(request, SecurityUtils.getCurrentUserId()));
    }

    @DeleteMapping("/{id}/purge")
    @Observed(name = "http.purgePTSession", contextualName = "DELETE /api/v1/pt-sessions/{id}/purge")
    @Operation(summary = "Permanently delete a cancelled PT session")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "PT session permanently deleted"),
            @ApiResponse(responseCode = "403", description = "Not your session"),
            @ApiResponse(responseCode = "400", description = "Only cancelled sessions can be purged")
    })
    public ResponseEntity<Void> purge(@PathVariable Long id) {
        ptSessionService.deletePermanently(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mine")
    @Operation(summary = "List the current user's sessions",
            description = "Returns sessions where the user is the member, or the trainer if the user is a TRAINER.")
    public ResponseEntity<PagedResponse<PTSessionResponse>> mySessions(
            @PageableDefault(size = 10) Pageable pageable) {
        var principal = SecurityUtils.getCurrentUser();
        return PagedResponse.ok(ptSessionService.getMySessions(principal.id(), principal.roleName(), pageable));
    }

    @DeleteMapping("/{id}")
    @Observed(name = "http.cancelPTSession", contextualName = "DELETE /api/v1/pt-sessions/{id}")
    @Operation(summary = "Cancel a scheduled session (participants only)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session cancelled"),
            @ApiResponse(responseCode = "403", description = "Not a participant of this session"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        ptSessionService.cancel(id, SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
