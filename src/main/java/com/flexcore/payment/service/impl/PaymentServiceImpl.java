package com.flexcore.payment.service.impl;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.core.exception.ResourceNotFoundException;
import com.flexcore.payment.dto.request.InitiatePaymentRequest;
import com.flexcore.payment.dto.response.PaymentResponse;
import com.flexcore.payment.entity.Payment;
import com.flexcore.payment.entity.PaymentIdempotency;
import com.flexcore.payment.enums.PaymentStatus;
import com.flexcore.payment.mapper.PaymentMapper;
import com.flexcore.payment.provider.MockPaymentGateway;
import com.flexcore.payment.repository.PaymentIdempotencyRepository;
import com.flexcore.payment.repository.PaymentRepository;
import com.flexcore.payment.service.PaymentIdempotencyWaiter;
import com.flexcore.payment.service.PaymentInProgressException;
import com.flexcore.payment.service.PaymentService;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.repository.SubscriptionRepository;
import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentIdempotencyRepository idempotencyRepository;
    private final PaymentIdempotencyWaiter idempotencyWaiter;
    private final SubscriptionRepository subscriptionRepository;
    private final MockPaymentGateway mockPaymentGateway;
    private final PaymentMapper paymentMapper;

    @Override
    @Observed(name = "payment.initiate", contextualName = "Initiate Payment")
    @Transactional
    public PaymentResponse initiate(InitiatePaymentRequest request, String idempotencyKey, Long currentUserId, boolean privileged) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return initiateIdempotent(request, idempotencyKey.trim(), currentUserId, privileged);
        }
        return paymentMapper.toResponse(doInitiate(request, currentUserId, privileged));
    }

    private PaymentResponse initiateIdempotent(InitiatePaymentRequest request, String idempotencyKey,
                                               Long currentUserId, boolean privileged) {
        java.util.Optional<PaymentIdempotency> existing =
                idempotencyRepository.findByUserIdAndIdempotencyKey(currentUserId, idempotencyKey);

        if (existing.isPresent() && existing.get().getPayment() != null) {
            log.debug("Idempotent replay for key={} user={}", idempotencyKey, currentUserId);
            return paymentMapper.toResponse(existing.get().getPayment());
        }

        int claimed = idempotencyRepository.claim(currentUserId, idempotencyKey);
        if (claimed == 0) {
            try {
                Payment resolved = idempotencyWaiter.awaitLinkedPayment(currentUserId, idempotencyKey);
                log.debug("Idempotent wait resolved for key={}", idempotencyKey);
                return paymentMapper.toResponse(resolved);
            } catch (PaymentInProgressException ex) {
                throw new BusinessRuleViolationException("error.payment.in-progress");
            }
        }

        Payment payment = doInitiate(request, currentUserId, privileged);
        idempotencyRepository.linkPayment(currentUserId, idempotencyKey, payment);

        return paymentMapper.toResponse(payment);
    }

    private Payment doInitiate(InitiatePaymentRequest request, Long currentUserId, boolean privileged) {
        Subscription subscription = subscriptionRepository.findByIdAndDeletedAtIsNull(request.getSubscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("error.subscription.notfound", request.getSubscriptionId()));

        if (!privileged && !subscription.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You can only pay for your own subscriptions");
        }
        if (subscription.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new BusinessRuleViolationException("error.payment.cancelled-subscription");
        }

        boolean approved = mockPaymentGateway.process(subscription.getPlan().getPrice(), request.getMethod());

        Payment payment = Payment.builder()
                .subscription(subscription)
                .amount(subscription.getPlan().getPrice())
                .method(request.getMethod())
                .status(approved ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .paidAt(approved ? LocalDateTime.now() : null)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment {} for subscription {} {}", payment.getId(), subscription.getId(),
                approved ? "succeeded" : "failed");

        if (approved && subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            LocalDateTime now = LocalDateTime.now();
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setStartDate(now);
            subscription.setEndDate(now.plusDays(subscription.getPlan().getDurationInDays()));
            subscriptionRepository.save(subscription);
        }

        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> getMyPayments(Long userId, Pageable pageable) {
        return paymentRepository.findBySubscriptionUserId(userId, pageable).map(paymentMapper::toResponse);
    }
}
