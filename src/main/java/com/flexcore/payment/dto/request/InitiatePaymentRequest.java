package com.flexcore.payment.dto.request;

import com.flexcore.payment.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Request to initiate a payment for a subscription (mock gateway)")
public class InitiatePaymentRequest {

    @NotNull(message = "{validation.subscription-id.required}")
    @Schema(description = "Subscription to pay for", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long subscriptionId;

    @NotNull(message = "{validation.payment-method.required}")
    @Schema(description = "Mock payment method", example = "MOCK_INSTAPAY", requiredMode = Schema.RequiredMode.REQUIRED)
    private PaymentMethod method;
}
