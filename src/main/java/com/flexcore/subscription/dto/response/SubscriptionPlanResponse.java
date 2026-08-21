package com.flexcore.subscription.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@Schema(description = "Subscription plan details")
public class SubscriptionPlanResponse {

    @Schema(description = "Plan id", example = "1")
    private Long id;

    @Schema(description = "Plan name", example = "Monthly Unlimited")
    private String name;

    @Schema(description = "Price in EGP", example = "1200.00")
    private BigDecimal price;

    @Schema(description = "Duration in days", example = "30")
    private int durationInDays;

    @Schema(description = "Max family members (null for individual plans)", example = "4")
    private Integer maxFamilyMembers;

    @Schema(description = "Whether this is a family plan", example = "false")
    private boolean familyPlan;
}
