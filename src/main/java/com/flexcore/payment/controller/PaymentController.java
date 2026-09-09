package com.flexcore.payment.controller;

import com.flexcore.core.dto.response.PagedResponse;
import com.flexcore.core.security.SecurityUtils;
import com.flexcore.payment.dto.request.InitiatePaymentRequest;
import com.flexcore.payment.dto.response.PaymentResponse;
import com.flexcore.payment.service.PaymentService;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Payments", description = "Mock payment processing for subscriptions")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @Observed(name = "http.initiatePayment", contextualName = "POST /api/v1/payments/initiate")
    @Operation(summary = "Initiate a payment for a subscription (mock gateway, ~90% success)",
            description = "Members pay for their own subscriptions. Staff with RENEW_SUBSCRIPTION may pay on behalf of others.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment processed (check status field)"),
            @ApiResponse(responseCode = "403", description = "Not your subscription"),
            @ApiResponse(responseCode = "404", description = "Subscription not found")
    })
    public ResponseEntity<PaymentResponse> initiate(@Valid @RequestBody InitiatePaymentRequest request) {
        boolean privileged = SecurityUtils.hasAuthority("RENEW_SUBSCRIPTION");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiate(request, SecurityUtils.getCurrentUserId(), privileged));
    }

    @GetMapping("/my")
    @Operation(summary = "List my payments (newest first)",
            description = "Returns the authenticated user's payment history across all their subscriptions. Pages are 1-indexed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of payments")
    })
    public ResponseEntity<PagedResponse<PaymentResponse>> my(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PagedResponse.ok(paymentService.getMyPayments(SecurityUtils.getCurrentUserId(), pageable));
    }
}
