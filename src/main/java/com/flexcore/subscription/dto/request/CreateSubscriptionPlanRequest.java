package com.flexcore.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Request to create a subscription plan")
public class CreateSubscriptionPlanRequest {

    @NotBlank(message = "{validation.plan-name.required}")
    @Size(max = 100, message = "{validation.plan-name.size}")
    @Schema(description = "Plan name", example = "Monthly Unlimited", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "{validation.price.required}")
    @DecimalMin(value = "0.0", inclusive = false, message = "{validation.price.positive}")
    @Schema(description = "Price in EGP", example = "1200.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @NotNull(message = "{validation.duration.required}")
    @Min(value = 1, message = "{validation.plan-duration.min}")
    @Schema(description = "Duration in days", example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
    private int durationInDays;

    @Schema(description = "Max family members (set only for family plans)", example = "4")
    private Integer maxFamilyMembers;
}
