package com.flexcore.report;

import com.flexcore.payment.repository.PaymentRepository;
import com.flexcore.report.dto.response.RevenueReportResponse;
import com.flexcore.report.service.impl.ReportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private ReportServiceImpl reportService;

    private PaymentRepository.MethodTotal row(String method, String total, long count) {
        PaymentRepository.MethodTotal row = org.mockito.Mockito.mock(PaymentRepository.MethodTotal.class);
        when(row.getMethod()).thenReturn(method);
        when(row.getTotal()).thenReturn(new BigDecimal(total));
        when(row.getCount()).thenReturn(count);
        return row;
    }

    @Test
    void revenue_aggregatesTotalsAcrossMethods() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);
        List<PaymentRepository.MethodTotal> rows = List.of(
                row("MOCK_FAWRY", "1500.00", 3),
                row("MOCK_INSTAPAY", "500.50", 1)
        );
        when(paymentRepository.aggregateTotalsByMethodBetween(start, end)).thenReturn(rows);

        RevenueReportResponse response = reportService.revenue(start, end);

        assertEquals(0, new BigDecimal("2000.50").compareTo(response.getTotalRevenue()));
        assertEquals(4, response.getTotalPayments());
        assertEquals(start, response.getStartDate());
        assertEquals(end, response.getEndDate());
        assertEquals(2, response.getBreakdown().size());
        assertEquals("MOCK_FAWRY", response.getBreakdown().get(0).getMethod());
    }

    @Test
    void revenue_defaultsToLast30DaysWhenRangeMissing() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(1);
        when(paymentRepository.aggregateTotalsByMethodBetween(any(), any())).thenReturn(List.of());

        RevenueReportResponse response = reportService.revenue(null, null);

        assertNotNull(response.getStartDate());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotalRevenue()));
        assertEquals(0, response.getTotalPayments());
        assertTrue(response.getBreakdown().isEmpty());
        assertTrue(response.getStartDate().isAfter(before.minusDays(31)));
        assertTrue(response.getEndDate().isAfter(LocalDateTime.now().minusMinutes(5)));
    }

    @Test
    void revenue_startOnlyUsesDefaultEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 2, 1, 8, 0);
        List<PaymentRepository.MethodTotal> rows = List.of(row("MOCK_VODAFONE_CASH", "300", 2));
        when(paymentRepository.aggregateTotalsByMethodBetween(any(), any())).thenReturn(rows);

        RevenueReportResponse response = reportService.revenue(start, null);

        assertEquals(start, response.getStartDate());
        assertNotNull(response.getEndDate());
        assertEquals(0, new BigDecimal("300").compareTo(response.getTotalRevenue()));
    }
}
