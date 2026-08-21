package com.flexcore.subscription.controller;

import com.flexcore.subscription.dto.request.CreateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.request.UpdateSubscriptionPlanRequest;
import com.flexcore.subscription.dto.response.SubscriptionPlanResponse;
import com.flexcore.subscription.service.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Subscription Plans", description = "Browse and manage subscription plans")
public class SubscriptionPlanController {

    private final SubscriptionPlanService planService;

    @PostMapping
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTIONS')")
    @Operation(summary = "Create a subscription plan", description = "Requires MANAGE_SUBSCRIPTIONS permission")
    @ApiResponse(responseCode = "201", description = "Plan created")
    public ResponseEntity<SubscriptionPlanResponse> create(@Valid @RequestBody CreateSubscriptionPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MANAGE_SUBSCRIPTIONS')")
    @Operation(summary = "Update a subscription plan", description = "Requires MANAGE_SUBSCRIPTIONS permission")
    public ResponseEntity<SubscriptionPlanResponse> update(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateSubscriptionPlanRequest request) {
        return ResponseEntity.ok(planService.update(id, request));
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "List all subscription plans (public)")
    public ResponseEntity<List<SubscriptionPlanResponse>> getAll() {
        return ResponseEntity.ok(planService.getAll());
    }
}
