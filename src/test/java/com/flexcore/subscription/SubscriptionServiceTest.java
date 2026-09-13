package com.flexcore.subscription;

import com.flexcore.core.exception.BusinessRuleViolationException;
import com.flexcore.subscription.dto.request.PurchaseSubscriptionRequest;
import com.flexcore.subscription.dto.response.SubscriptionResponse;
import com.flexcore.subscription.entity.Subscription;
import com.flexcore.subscription.entity.SubscriptionPlan;
import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.mapper.SubscriptionMapper;
import com.flexcore.subscription.repository.FamilyGroupRepository;
import com.flexcore.subscription.repository.SubscriptionPlanRepository;
import com.flexcore.subscription.repository.SubscriptionRepository;
import com.flexcore.subscription.service.impl.SubscriptionServiceImpl;
import com.flexcore.user.entity.User;
import com.flexcore.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private FamilyGroupRepository familyGroupRepository;
    @Mock private UserRepository userRepository;
    @Mock private SubscriptionMapper subscriptionMapper;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private User user;
    private SubscriptionPlan monthlyPlan;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).fullName("Sara Ali").email("sara@example.com").active(true).build();
        monthlyPlan = SubscriptionPlan.builder()
                .id(10L)
                .name("Monthly Unlimited")
                .price(new BigDecimal("1200.00"))
                .durationInDays(30)
                .build();
    }

    @Test
    void purchase_createsActiveSubscriptionEndingAfterPlanDuration() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(planRepository.findById(10L)).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.existsByUserIdAndDeletedAtIsNullAndStatusIn(1L,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN))).thenReturn(false);
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toResponse(any(Subscription.class)))
                .thenReturn(SubscriptionResponse.builder().id(50L).status(SubscriptionStatus.ACTIVE).build());

        PurchaseSubscriptionRequest request = new PurchaseSubscriptionRequest();
        request.setUserId(null);
        request.setPlanId(10L);

        SubscriptionResponse response = subscriptionService.purchase(request, 1L, false);

        assertNotNull(response);
        assertEquals(SubscriptionStatus.ACTIVE, response.getStatus());
    }

    @Test
    void purchase_whenUserAlreadyHasActiveSubscription_throws() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(planRepository.findById(10L)).thenReturn(Optional.of(monthlyPlan));
        when(subscriptionRepository.existsByUserIdAndDeletedAtIsNullAndStatusIn(1L,
                List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.FROZEN))).thenReturn(true);

        PurchaseSubscriptionRequest request = new PurchaseSubscriptionRequest();
        request.setPlanId(10L);

        assertThrows(BusinessRuleViolationException.class,
                () -> subscriptionService.purchase(request, 1L, false));
    }

    @Test
    void freeze_whenSubscriptionNotActive_throws() {
        Subscription expired = Subscription.builder()
                .id(7L)
                .user(user)
                .plan(monthlyPlan)
                .status(SubscriptionStatus.EXPIRED)
                .startDate(java.time.LocalDateTime.now().minusDays(60))
                .endDate(java.time.LocalDateTime.now().minusDays(30))
                .build();

        when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(expired));

        var freezeRequest = new com.flexcore.subscription.dto.request.FreezeSubscriptionRequest();
        freezeRequest.setDays(14);

        assertThrows(BusinessRuleViolationException.class,
                () -> subscriptionService.freeze(7L, freezeRequest, 1L, false));
    }

    @Test
    void freeze_whenActiveButEndDateAlreadyPassed_throws() {
        Subscription overdue = Subscription.builder()
                .id(7L)
                .user(user)
                .plan(monthlyPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDateTime.now().minusDays(60))
                .endDate(java.time.LocalDateTime.now().minusDays(1))
                .build();

        when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(overdue));

        var freezeRequest = new com.flexcore.subscription.dto.request.FreezeSubscriptionRequest();
        freezeRequest.setDays(14);

        assertThrows(BusinessRuleViolationException.class,
                () -> subscriptionService.freeze(7L, freezeRequest, 1L, false));
        assertEquals(SubscriptionStatus.ACTIVE, overdue.getStatus());
    }

    @Test
    void freeze_whenActiveAndNotEnded_freezesAndExtendsEndDate() {
        java.time.LocalDateTime originalEnd = java.time.LocalDateTime.now().plusDays(20);
        Subscription active = Subscription.builder()
                .id(7L)
                .user(user)
                .plan(monthlyPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDateTime.now().minusDays(10))
                .endDate(originalEnd)
                .build();

        when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(active));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toResponse(any(Subscription.class)))
                .thenReturn(SubscriptionResponse.builder().id(7L).status(SubscriptionStatus.FROZEN).build());

        var freezeRequest = new com.flexcore.subscription.dto.request.FreezeSubscriptionRequest();
        freezeRequest.setDays(14);

        SubscriptionResponse response = subscriptionService.freeze(7L, freezeRequest, 1L, false);

        assertEquals(SubscriptionStatus.FROZEN, active.getStatus());
        assertNotNull(active.getFrozenUntil());
        assertEquals(originalEnd.plusDays(14).withNano(0), active.getEndDate().withNano(0));
        assertEquals(SubscriptionStatus.FROZEN, response.getStatus());
    }

    @Test
    void cancel_setsStatusToCancelled() {
        Subscription active = Subscription.builder()
                .id(7L)
                .user(user)
                .plan(monthlyPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(java.time.LocalDateTime.now())
                .endDate(java.time.LocalDateTime.now().plusDays(20))
                .build();

        when(subscriptionRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(active));
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subscriptionMapper.toResponse(any(Subscription.class)))
                .thenReturn(SubscriptionResponse.builder().id(7L).status(SubscriptionStatus.CANCELLED).build());

        SubscriptionResponse response = subscriptionService.cancel(7L, 1L, false);

        assertEquals(SubscriptionStatus.CANCELLED, active.getStatus());
        assertEquals(SubscriptionStatus.CANCELLED, response.getStatus());
    }
}
