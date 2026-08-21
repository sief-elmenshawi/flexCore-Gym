package com.flexcore.payment.service;

import com.flexcore.payment.dto.request.InitiatePaymentRequest;
import com.flexcore.payment.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {

    PaymentResponse initiate(InitiatePaymentRequest request, Long currentUserId, boolean privileged);

    Page<PaymentResponse> getMyPayments(Long userId, Pageable pageable);
}
