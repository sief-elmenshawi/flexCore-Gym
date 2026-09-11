package com.flexcore.payment.service;

/**
 * Internal retry signal used by {@link PaymentIdempotencyWaiter}: thrown while an
 * idempotency key is claimed by a concurrent request but its payment is not linked
 * yet. Retried by Spring Retry; never leaves the service layer — the waiter converts
 * it to {@link com.flexcore.core.exception.BusinessRuleViolationException} after the
 * retry budget is exhausted.
 */
public class PaymentInProgressException extends RuntimeException {

    public PaymentInProgressException(String message) {
        super(message);
    }
}