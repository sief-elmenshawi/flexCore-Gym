package com.flexcore.report.service;

import com.flexcore.report.dto.response.RevenueReportResponse;

import java.time.LocalDateTime;

public interface ReportService {

    RevenueReportResponse revenue(LocalDateTime start, LocalDateTime end);
}
