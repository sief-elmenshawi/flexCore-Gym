package com.flexcore.subscription.scheduler;

import com.flexcore.subscription.enums.SubscriptionStatus;
import com.flexcore.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirySchedulerTest {

    @Mock private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionExpiryScheduler scheduler;

    @Test
    void maintainSubscriptionStates_runsExpireAndThaw() {
        when(subscriptionRepository.thawElapsedFreezes(any())).thenReturn(2);
        when(subscriptionRepository.expireOverdue(any(), eq(SubscriptionStatus.EXPIRED))).thenReturn(1);

        scheduler.maintainSubscriptionStates();

        verify(subscriptionRepository).thawElapsedFreezes(any());
        verify(subscriptionRepository).expireOverdue(any(), eq(SubscriptionStatus.EXPIRED));
    }

    @Test
    void maintainSubscriptionStates_withNothingToDo_completesQuietly() {
        when(subscriptionRepository.thawElapsedFreezes(any())).thenReturn(0);
        when(subscriptionRepository.expireOverdue(any(), eq(SubscriptionStatus.EXPIRED))).thenReturn(0);

        scheduler.maintainSubscriptionStates();

        verify(subscriptionRepository).thawElapsedFreezes(any());
        verify(subscriptionRepository).expireOverdue(any(), eq(SubscriptionStatus.EXPIRED));
    }
}
