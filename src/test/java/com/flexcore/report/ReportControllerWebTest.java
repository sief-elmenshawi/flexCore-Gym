package com.flexcore.report;

import com.flexcore.core.config.SecurityConfiguration;
import com.flexcore.core.security.SecurityProblemHandler;
import com.flexcore.report.controller.ReportController;
import com.flexcore.report.dto.response.RevenueReportResponse;
import com.flexcore.report.service.ReportService;
import com.flexcore.support.SecuredControllerSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import({SecurityConfiguration.class, SecurityProblemHandler.class})
class ReportControllerWebTest extends SecuredControllerSliceTest {

    @MockitoBean
    private ReportService reportService;

    @Test
    void revenue_withViewReportsPermission_returns200() throws Exception {
        when(reportService.revenue(any(), any())).thenReturn(RevenueReportResponse.builder()
                .totalRevenue(new BigDecimal("100.00"))
                .totalPayments(2)
                .build());

        mockMvc.perform(get("/api/v1/reports/revenue").with(asUser(9L, "ADMIN", "VIEW_REPORTS"))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void revenue_explicitRangeIsPassedToService() throws Exception {
        when(reportService.revenue(any(), any()))
                .thenReturn(RevenueReportResponse.builder().build());

        mockMvc.perform(get("/api/v1/reports/revenue").with(asUser(9L, "ADMIN", "VIEW_REPORTS"))
                        .param("start", "2026-01-01T00:00:00")
                        .param("end", "2026-01-31T23:59:59"))
                .andExpect(status().isOk());

        verify(reportService).revenue(eq(java.time.LocalDateTime.of(2026, 1, 1, 0, 0)),
                eq(java.time.LocalDateTime.of(2026, 1, 31, 23, 59, 59)));
    }

    @Test
    void revenue_withoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reports/revenue").with(asUser(7L, "MEMBER", "BOOK_CLASS")))
                .andExpect(status().isForbidden());

        verify(reportService, never()).revenue(any(), any());
    }
}
