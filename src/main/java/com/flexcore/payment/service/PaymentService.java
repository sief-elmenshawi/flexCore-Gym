package com.flexcore.payment.service;

import com.flexcore.payment.dto.request.InitiatePaymentRequest;
import com.flexcore.payment.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse initiate(InitiatePaymentRequest request, Long currentUserId, boolean privileged);
}
