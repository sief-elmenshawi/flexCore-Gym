package com.flexcore.payment.dto.response;

import com.flexcore.payment.enums.PaymentMethod;
import com.flexcore.payment.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "Payment details")
public class PaymentResponse {

    @Schema(description = "Payment id", example = "4")
    private Long id;

    @Schema(description = "Subscription id", example = "7")
    private Long subscriptionId;

    @Schema(description = "Amount in EGP", example = "1200.00")
    private BigDecimal amount;

    @Schema(description = "Payment method", example = "MOCK_INSTAPAY")
    private PaymentMethod method;

    @Schema(description = "Payment status", example = "SUCCESS")
    private PaymentStatus status;

    @Schema(description = "When the payment completed", example = "2026-08-21T12:30:00")
    private LocalDateTime paidAt;
}
