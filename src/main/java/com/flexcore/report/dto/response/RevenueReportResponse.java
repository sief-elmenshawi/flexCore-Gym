package com.flexcore.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "Revenue report for a date range")
public class RevenueReportResponse {

    @Schema(description = "Range start (inclusive)", example = "2026-08-01T00:00:00")
    private LocalDateTime startDate;

    @Schema(description = "Range end (inclusive)", example = "2026-08-31T23:59:59")
    private LocalDateTime endDate;

    @Schema(description = "Total collected revenue in EGP", example = "45800.00")
    private BigDecimal totalRevenue;

    @Schema(description = "Number of successful payments", example = "38")
    private long totalPayments;

    @Schema(description = "Revenue breakdown per payment method")
    private List<MethodBreakdown> breakdown;

    @Getter
    @Builder
    @Schema(description = "Revenue per payment method")
    public static class MethodBreakdown {

        @Schema(description = "Payment method", example = "MOCK_INSTAPAY")
        private String method;

        @Schema(description = "Total amount for this method", example = "25000.00")
        private BigDecimal total;

        @Schema(description = "Number of payments for this method", example = "21")
        private long count;
    }
}
