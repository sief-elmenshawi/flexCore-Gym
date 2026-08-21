package com.flexcore.subscription.dto.response;

import com.flexcore.subscription.enums.SubscriptionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Subscription details")
public class SubscriptionResponse {

    @Schema(description = "Subscription id", example = "7")
    private Long id;

    @Schema(description = "Subscriber user id", example = "5")
    private Long userId;

    @Schema(description = "Subscriber full name", example = "Sara Ali")
    private String userName;

    @Schema(description = "Plan id", example = "1")
    private Long planId;

    @Schema(description = "Plan name", example = "Monthly Unlimited")
    private String planName;

    @Schema(description = "Plan price in EGP", example = "1200.00")
    private BigDecimal price;

    @Schema(description = "Subscription status", example = "ACTIVE")
    private SubscriptionStatus status;

    @Schema(description = "Start date", example = "2026-08-01T00:00:00")
    private LocalDateTime startDate;

    @Schema(description = "End date", example = "2026-08-31T00:00:00")
    private LocalDateTime endDate;

    @Schema(description = "When the subscription was frozen", example = "2026-08-10T00:00:00")
    private LocalDateTime frozenAt;

    @Schema(description = "Freeze end date", example = "2026-08-24T00:00:00")
    private LocalDateTime frozenUntil;

    @Schema(description = "Family group id if part of one", example = "3")
    private Long familyGroupId;
}
