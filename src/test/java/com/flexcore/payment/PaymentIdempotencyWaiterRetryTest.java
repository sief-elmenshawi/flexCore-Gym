package com.flexcore.payment.service;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.payment.entity.Payment;
import com.flexcore.payment.entity.PaymentIdempotency;
import com.flexcore.payment.repository.PaymentIdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the real {@code @Retryable} AOP proxy (Spring Boot 4 built-in):
 * the lookup is retried while the payment is unlinked, and the retry budget
 * maps to a {@link PaymentInProgressException} that the service translates
 * into a client-facing {@link BusinessRuleViolationException}.
 * <p>
 * A plain unit test would bypass the proxy; this boots a minimal Spring context
 * with {@link EnableResilientMethods} enabled.
 */
@SpringJUnitConfig(classes = PaymentIdempotencyWaiterRetryTest.RetryConfig.class)
class PaymentIdempotencyWaiterRetryTest {

    @Configuration
    @EnableResilientMethods
    static class RetryConfig {
        @Bean
        PaymentIdempotencyWaiter waiter(PaymentIdempotencyRepository repository) {
            return new PaymentIdempotencyWaiter(repository);
        }
    }

    @MockitoBean
    private PaymentIdempotencyRepository repository;

    @Autowired
    private PaymentIdempotencyWaiter waiter;

    private Payment payment;
    private PaymentIdempotency linked;

    @BeforeEach
    void setUp() {
        payment = Payment.builder().id(5L).build();
        linked = new PaymentIdempotency();
        linked.setPayment(payment);
    }

    @Test
    void returnsPaymentImmediately_whenAlreadyLinked() {
        when(repository.findByUserIdAndIdempotencyKey(9L, "key1")).thenReturn(java.util.Optional.of(linked));

        var result = waiter.awaitLinkedPayment(9L, "key1");

        assertEquals(5L, result.getId());
        verify(repository, times(1)).findByUserIdAndIdempotencyKey(eq(9L), eq("key1"));
    }

    @Test
    void retriesWhenPaymentNotYetLinked_untilAppears() {
        when(repository.findByUserIdAndIdempotencyKey(9L, "late"))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(linked));

        var result = waiter.awaitLinkedPayment(9L, "late");

        assertEquals(5L, result.getId());
        verify(repository, times(3)).findByUserIdAndIdempotencyKey(eq(9L), eq("late"));
    }

    @Test
    void givesUpAfterRetryBudget_throwsPaymentInProgressException() {
        when(repository.findByUserIdAndIdempotencyKey(9L, "stuck")).thenReturn(java.util.Optional.empty());

        assertThrows(PaymentInProgressException.class, () -> waiter.awaitLinkedPayment(9L, "stuck"));

        verify(repository, times(5)).findByUserIdAndIdempotencyKey(eq(9L), eq("stuck"));
    }

    @Test
    void backoffIsApplied_betweenAttempts() {
        when(repository.findByUserIdAndIdempotencyKey(9L, "timed"))
                .thenReturn(java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.of(linked));

        long started = System.nanoTime();
        waiter.awaitLinkedPayment(9L, "timed");
        long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        verify(repository, times(3)).findByUserIdAndIdempotencyKey(eq(9L), eq("timed"));
        org.junit.jupiter.api.Assertions.assertTrue(elapsedMs >= 100,
                () -> "expected at least one 100ms backoff between retries, took " + elapsedMs + "ms");
    }
}