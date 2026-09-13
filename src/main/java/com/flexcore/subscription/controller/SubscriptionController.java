package com.flexcore.subscription.controller;

import com.flexcore.core.dto.response.PagedResponse;
import com.flexcore.core.security.SecurityUtils;
import com.flexcore.subscription.dto.request.FreezeSubscriptionRequest;
import com.flexcore.subscription.dto.request.PurchaseSubscriptionRequest;
import com.flexcore.subscription.dto.response.SubscriptionResponse;
import com.flexcore.subscription.service.SubscriptionService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Subscriptions", description = "Purchase, freeze, unfreeze and cancel subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping("/purchase")
    @PreAuthorize("hasAuthority('BOOK_CLASS')")
    @Observed(name = "http.purchaseSubscription", contextualName = "POST /api/v1/subscriptions/purchase")
    @Operation(summary = "Purchase a subscription",
            description = "Members buy for themselves. Staff with RENEW_SUBSCRIPTION may pass userId to buy for others.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subscription created"),
            @ApiResponse(responseCode = "400", description = "User already has an active/frozen subscription"),
            @ApiResponse(responseCode = "404", description = "Plan or family group not found")
    })
    public ResponseEntity<SubscriptionResponse> purchase(@Valid @RequestBody PurchaseSubscriptionRequest request) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION")
                || SecurityUtils.hasAuthority("MANAGE_SUBSCRIPTIONS");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.purchase(request, SecurityUtils.getCurrentUserId(), privileged));
    }

    @PostMapping("/{id}/freeze")
    @Observed(name = "http.freezeSubscription", contextualName = "POST /api/v1/subscriptions/{id}/freeze")
    @Operation(summary = "Freeze a subscription for a number of days (extends the end date)")
    public ResponseEntity<SubscriptionResponse> freeze(@PathVariable Long id,
                                                       @Valid @RequestBody FreezeSubscriptionRequest request) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION")
                || SecurityUtils.hasAuthority("MANAGE_SUBSCRIPTIONS");
        return ResponseEntity.ok(subscriptionService.freeze(id, request, SecurityUtils.getCurrentUserId(), privileged));
    }

    @PostMapping("/{id}/unfreeze")
    @Observed(name = "http.unfreezeSubscription", contextualName = "POST /api/v1/subscriptions/{id}/unfreeze")
    @Operation(summary = "Unfreeze a frozen subscription")
    public ResponseEntity<SubscriptionResponse> unfreeze(@PathVariable Long id) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION")
                || SecurityUtils.hasAuthority("MANAGE_SUBSCRIPTIONS");
        return ResponseEntity.ok(subscriptionService.unfreeze(id, SecurityUtils.getCurrentUserId(), privileged));
    }

    @DeleteMapping("/{id}")
    @Observed(name = "http.cancelSubscription", contextualName = "DELETE /api/v1/subscriptions/{id}")
    @Operation(summary = "Cancel a subscription (or permanently purge an already-cancelled one)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription cancelled"),
            @ApiResponse(responseCode = "204", description = "Subscription permanently deleted"),
            @ApiResponse(responseCode = "403", description = "Not your subscription")
    })
    public ResponseEntity<SubscriptionResponse> cancel(@PathVariable Long id) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION")
                || SecurityUtils.hasAuthority("MANAGE_SUBSCRIPTIONS");
        return ResponseEntity.ok(subscriptionService.cancel(id, SecurityUtils.getCurrentUserId(), privileged));
    }

    @PostMapping("/{id}/reactivate")
    @Observed(name = "http.reactivateSubscription", contextualName = "POST /api/v1/subscriptions/{id}/reactivate")
    @Operation(summary = "Reactivate a cancelled subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription reactivated"),
            @ApiResponse(responseCode = "400", description = "Not cancelled or already active"),
            @ApiResponse(responseCode = "403", description = "Not your subscription")
    })
    public ResponseEntity<SubscriptionResponse> reactivate(@PathVariable Long id) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION")
                || SecurityUtils.hasAuthority("MANAGE_SUBSCRIPTIONS");
        return ResponseEntity.ok(subscriptionService.reactivate(id, SecurityUtils.getCurrentUserId(), privileged));
    }

    @DeleteMapping("/{id}/purge")
    @Observed(name = "http.purgeSubscription", contextualName = "DELETE /api/v1/subscriptions/{id}/purge")
    @Operation(summary = "Permanently delete a cancelled or expired subscription")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Subscription permanently deleted"),
            @ApiResponse(responseCode = "403", description = "Not your subscription"),
            @ApiResponse(responseCode = "400", description = "Only cancelled or expired subscriptions can be purged")
    })
    public ResponseEntity<Void> purge(@PathVariable Long id) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION")
                || SecurityUtils.hasAuthority("MANAGE_SUBSCRIPTIONS");
        subscriptionService.deletePermanently(id, SecurityUtils.getCurrentUserId(), privileged);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    @Operation(summary = "List the current user's subscriptions ordered by end date")
    public ResponseEntity<List<SubscriptionResponse>> mySubscriptions() {
        return ResponseEntity.ok(subscriptionService.getMySubscriptions(SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/expiring-soon")
    @PreAuthorize("hasAuthority('VIEW_REPORTS')")
    @Operation(summary = "List active subscriptions expiring within N days",
            description = "Requires VIEW_REPORTS permission. Pages are 1-indexed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of soon-expiring subscriptions"),
            @ApiResponse(responseCode = "400", description = "withinDays must be between 1 and 90")
    })
    public ResponseEntity<PagedResponse<SubscriptionResponse>> expiringSoon(
            @RequestParam(defaultValue = "7") @jakarta.validation.constraints.Min(value = 1)
            @jakarta.validation.constraints.Max(90) int withinDays,
            @PageableDefault(size = 10) Pageable pageable) {
        return PagedResponse.ok(subscriptionService.expiringSoon(withinDays, pageable));
    }
}
