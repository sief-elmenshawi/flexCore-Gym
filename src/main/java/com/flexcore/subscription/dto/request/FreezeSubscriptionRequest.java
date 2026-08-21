package com.flexcore.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to freeze a subscription for a number of days")
public class FreezeSubscriptionRequest {

    @NotNull(message = "{validation.freeze-days.required}")
    @Min(value = 1, message = "{validation.freeze-days.min}")
    @Max(value = 60, message = "{validation.freeze-days.max}")
    @Schema(description = "Number of days to freeze", example = "14", requiredMode = Schema.RequiredMode.REQUIRED)
    private int days;
}
