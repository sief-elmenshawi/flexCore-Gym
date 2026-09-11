package com.flexcore.payment.service;

import com.flexcore.payment.entity.Payment;
import com.flexcore.payment.entity.PaymentIdempotency;
import com.flexcore.payment.repository.PaymentIdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Owns the idempotency wait-and-resolve step that used to live as a hand-rolled
 * {@code Thread.sleep} loop inside {@link PaymentServiceImpl}.
 * <p>
 * When a concurrent request wins a key claim, the other requester must not charge;
 * it waits for the winner to link its payment. The fixed 100ms-sleep loop is replaced
 * by Spring's core {@code @Retryable} (Spring Boot 4 / Framework 7 built-in): the lookup
 * is retried up to {@code maxRetries} times with a growing backoff, and the eventual
 * {@link PaymentInProgressException} is translated by the caller into a 409 client error.
 * <p>
 * {@code @Retryable} is proxied by Spring AOP, so this must live in its own bean and be
 * invoked through the proxy — never as a self-call from {@link PaymentServiceImpl}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentIdempotencyWaiter {

    private final PaymentIdempotencyRepository idempotencyRepository;

    @Retryable(includes = PaymentInProgressException.class,
            maxRetries = 4,
            delay = 100,
            multiplier = 1.5)
    public Payment awaitLinkedPayment(Long userId, String idempotencyKey) {
        return idempotencyRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .filter(entry -> entry.getPayment() != null)
                .map(PaymentIdempotency::getPayment)
                .orElseThrow(() -> new PaymentInProgressException("Payment still in progress for key=" + idempotencyKey));
    }
}