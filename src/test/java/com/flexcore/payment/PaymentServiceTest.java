package com.flexcore.payment;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.payment.dto.request.InitiatePaymentRequest;
import com.flexcore.payment.dto.response.PaymentResponse;
import com.flexcore.payment.entity.Payment;
import com.flexcore.payment.enums.PaymentMethod;
import com.flexcore.payment.enums.PaymentStatus;
import com.flexcore.payment.mapper.PaymentMapper;
import com.flexcore.payment.provider.MockPaymentGateway;
import com.flexcore.payment.repository.PaymentIdempotencyRepository;
import com.flexcore.payment.repository.PaymentRepository;
import com.flexcore.payment.service.PaymentIdempotencyWaiter;
import com.flexcore.payment.service.PaymentInProgressException;
import com.flexcore.payment.service.impl.PaymentServiceImpl;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentIdempotencyRepository idempotencyRepository;
    @Mock private PaymentIdempotencyWaiter idempotencyWaiter;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private MockPaymentGateway mockPaymentGateway;
    @Mock private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User user;
    private SubscriptionPlan monthlyPlan;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        user = User.builder().id(3L).fullName("Ahmed Test").email("ahmed@test.com").active(true).build();
        monthlyPlan = SubscriptionPlan.builder()
                .id(10L)
                .name("Monthly Unlimited")
                .price(new BigDecimal("1200.00"))
                .durationInDays(30)
                .build();
        subscription = Subscription.builder()
                .id(7L)
                .user(user)
                .plan(monthlyPlan)
                .status(SubscriptionStatus.EXPIRED)
                .startDate(LocalDateTime.now().minusDays(60))
                .endDate(LocalDateTime.now().minusDays(30))
                .build();
    }

    private InitiatePaymentRequest request(PaymentMethod method) {
        InitiatePaymentRequest request = new InitiatePaymentRequest();
        request.setSubscriptionId(7L);
        request.setMethod(method);
        return request;
    }

    @Test
    void initiate_whenExpiredSubscriptionPaidSuccessfully_renewsFromNow() {
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(mockPaymentGateway.process(monthlyPlan.getPrice(), PaymentMethod.MOCK_INSTAPAY)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), null, 3L, false);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertTrue(subscription.getStartDate().isAfter(before));
        assertTrue(subscription.getEndDate().isAfter(before));
        assertTrue(subscription.getEndDate().isBefore(after.plusDays(31)));
        assertEquals(30, java.time.Duration.between(
                subscription.getStartDate(), subscription.getEndDate()).toDays());
        verify(subscriptionRepository).save(subscription);
    }

    @Test
    void initiate_whenSubscriptionCancelled_throwsWithoutCharging() {
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));

        assertThrows(BusinessRuleViolationException.class,
                () -> paymentService.initiate(request(PaymentMethod.MOCK_FAWRY), null, 3L, false));

        verify(mockPaymentGateway, never()).process(any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void initiate_whenGatewayRejects_paymentFailedAndNoRenewal() {
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(mockPaymentGateway.process(monthlyPlan.getPrice(), PaymentMethod.MOCK_VODAFONE_CASH)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.initiate(request(PaymentMethod.MOCK_VODAFONE_CASH), null, 3L, false);

        assertEquals(SubscriptionStatus.EXPIRED, subscription.getStatus());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void initiate_whenActiveSubscriptionPaid_doesNotRestartWindow() {
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        LocalDateTime originalEnd = LocalDateTime.now().plusDays(12);
        subscription.setStartDate(LocalDateTime.now().minusDays(18));
        subscription.setEndDate(originalEnd);
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(mockPaymentGateway.process(monthlyPlan.getPrice(), PaymentMethod.MOCK_INSTAPAY)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), null, 3L, false);

        assertEquals(originalEnd.withNano(0), subscription.getEndDate().withNano(0));
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void initiate_onSuccess_persistsSuccessfulPaymentWithPaidAt() {
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(mockPaymentGateway.process(monthlyPlan.getPrice(), PaymentMethod.MOCK_INSTAPAY)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), null, 3L, false);

        org.mockito.ArgumentCaptor<Payment> captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.SUCCESS, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getPaidAt());
        assertEquals(monthlyPlan.getPrice(), captor.getValue().getAmount());
    }

    @Test
    void initiate_onFailure_persistsFailedPaymentWithoutPaidAt() {
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(mockPaymentGateway.process(monthlyPlan.getPrice(), PaymentMethod.MOCK_INSTAPAY)).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), null, 3L, false);

        org.mockito.ArgumentCaptor<Payment> captor = org.mockito.ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getStatus());
        assertNull(captor.getValue().getPaidAt());
    }

    @Test
    void getMyPayments_returnsMappedPageForUser() {
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, 10);
        Payment payment = Payment.builder()
                .id(9L)
                .subscription(subscription)
                .amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY)
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();
        org.springframework.data.domain.Page<Payment> page =
                new org.springframework.data.domain.PageImpl<>(List.of(payment), pageable, 1);
        when(paymentRepository.findBySubscriptionUserId(3L, pageable)).thenReturn(page);
        when(paymentMapper.toResponse(payment)).thenReturn(PaymentResponse.builder()
                .id(9L)
                .subscriptionId(7L)
                .amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY)
                .status(PaymentStatus.SUCCESS)
                .build());

        var result = paymentService.getMyPayments(3L, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(9L, result.getContent().get(0).getId());
        verify(paymentRepository).findBySubscriptionUserId(3L, pageable);
    }

    @Test
    void initiate_withIdempotencyKey_sameKeyReplaysWithoutSecondGatewayCall() {
        var existingPayment = Payment.builder()
                .id(20L)
                .subscription(subscription)
                .amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY)
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();
        var idem = new com.flexcore.payment.entity.PaymentIdempotency();
        idem.setPayment(existingPayment);

        when(idempotencyRepository.findByUserIdAndIdempotencyKey(3L, "same-key"))
                .thenReturn(java.util.Optional.of(idem));
        when(paymentMapper.toResponse(existingPayment)).thenReturn(PaymentResponse.builder()
                .id(20L).subscriptionId(7L).amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY).status(PaymentStatus.SUCCESS).build());

        PaymentResponse response = paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), "same-key", 3L, false);

        assertEquals(20L, response.getId());
        verify(mockPaymentGateway, never()).process(any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void initiate_withIdempotencyKey_firstCallChargesAndLinksPayment() {
        PaymentResponse mapped = PaymentResponse.builder()
                .id(30L).subscriptionId(7L).amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY).status(PaymentStatus.SUCCESS).build();

        when(idempotencyRepository.findByUserIdAndIdempotencyKey(3L, "new-key"))
                .thenReturn(java.util.Optional.empty());
        when(idempotencyRepository.claim(3L, "new-key")).thenReturn(1);
        when(subscriptionRepository.findById(7L)).thenReturn(Optional.of(subscription));
        when(mockPaymentGateway.process(monthlyPlan.getPrice(), PaymentMethod.MOCK_INSTAPAY)).thenReturn(true);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment saved = inv.getArgument(0);
            saved.setId(30L);
            return saved;
        });
        when(paymentMapper.toResponse(any(Payment.class))).thenReturn(mapped);

        PaymentResponse response = paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), "new-key", 3L, false);

        assertEquals(30L, response.getId());
        verify(mockPaymentGateway).process(monthlyPlan.getPrice(), PaymentMethod.MOCK_INSTAPAY);
        verify(idempotencyRepository).claim(3L, "new-key");
        verify(idempotencyRepository).linkPayment(eq(3L), eq("new-key"), any(Payment.class));
    }

    @Test
    void initiate_withIdempotencyKeyConcurrentClaim_throwsBusinessRuleViolationAfterBudget() {
        when(idempotencyRepository.findByUserIdAndIdempotencyKey(3L, "busy-key"))
                .thenReturn(java.util.Optional.empty());
        when(idempotencyRepository.claim(3L, "busy-key")).thenReturn(0);
        when(idempotencyWaiter.awaitLinkedPayment(3L, "busy-key"))
                .thenThrow(new PaymentInProgressException("Payment still in progress for key=busy-key"));

        assertThrows(BusinessRuleViolationException.class,
                () -> paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), "busy-key", 3L, false));

        verify(mockPaymentGateway, never()).process(any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(idempotencyWaiter).awaitLinkedPayment(3L, "busy-key");
    }

    @Test
    void initiate_withIdempotencyKeyConcurrentClaim_returnsWhenWaiterResolves() {
        Payment existingPayment = Payment.builder()
                .id(31L)
                .subscription(subscription)
                .amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY)
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now())
                .build();
        PaymentResponse mapped = PaymentResponse.builder()
                .id(31L).subscriptionId(7L).amount(monthlyPlan.getPrice())
                .method(PaymentMethod.MOCK_INSTAPAY).status(PaymentStatus.SUCCESS).build();

        when(idempotencyRepository.findByUserIdAndIdempotencyKey(3L, "busy-key"))
                .thenReturn(java.util.Optional.empty());
        when(idempotencyRepository.claim(3L, "busy-key")).thenReturn(0);
        when(idempotencyWaiter.awaitLinkedPayment(3L, "busy-key")).thenReturn(existingPayment);
        when(paymentMapper.toResponse(existingPayment)).thenReturn(mapped);

        PaymentResponse response = paymentService.initiate(request(PaymentMethod.MOCK_INSTAPAY), "busy-key", 3L, false);

        assertEquals(31L, response.getId());
        verify(mockPaymentGateway, never()).process(any(), any());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
