package com.flexcore.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to purchase a subscription. Staff with RENEW_SUBSCRIPTION may buy on behalf of another user.")
public class PurchaseSubscriptionRequest {

    @Schema(description = "User to subscribe (defaults to the current user)", example = "5")
    private Long userId;

    @NotNull(message = "{validation.plan-id.required}")
    @Schema(description = "Subscription plan id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long planId;

    @Schema(description = "Family group to attach the subscription to (family plans only)", example = "3")
    private Long familyGroupId;
}
