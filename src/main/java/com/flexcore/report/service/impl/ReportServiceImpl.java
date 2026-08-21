package com.flexcore.report.service.impl;

import com.flexcore.payment.repository.PaymentRepository;
import com.flexcore.report.dto.response.RevenueReportResponse;
import com.flexcore.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final PaymentRepository paymentRepository;

    @Override
    @Transactional(readOnly = true)
    public RevenueReportResponse revenue(LocalDateTime start, LocalDateTime end) {
        LocalDateTime rangeEnd = end != null ? end : LocalDateTime.now();
        LocalDateTime rangeStart = start != null ? start : rangeEnd.minusDays(30);

        List<PaymentRepository.MethodTotal> rows =
                paymentRepository.aggregateTotalsByMethodBetween(rangeStart, rangeEnd);

        BigDecimal totalRevenue = rows.stream()
                .map(PaymentRepository.MethodTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalPayments = rows.stream()
                .mapToLong(PaymentRepository.MethodTotal::getCount)
                .sum();

        List<RevenueReportResponse.MethodBreakdown> breakdown = rows.stream()
                .map(row -> RevenueReportResponse.MethodBreakdown.builder()
                        .method(row.getMethod())
                        .total(row.getTotal())
                        .count(row.getCount())
                        .build())
                .toList();

        return RevenueReportResponse.builder()
                .startDate(rangeStart)
                .endDate(rangeEnd)
                .totalRevenue(totalRevenue)
                .totalPayments(totalPayments)
                .breakdown(breakdown)
                .build();
    }
}
