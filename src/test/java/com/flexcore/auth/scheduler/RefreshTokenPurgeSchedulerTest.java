package com.flexcore.auth.scheduler;

import com.flexcore.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenPurgeSchedulerTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks private RefreshTokenPurgeScheduler scheduler;

    @Test
    void purge_detachesRotationLinksBeforeDeletingEligibleTokens() {
        when(refreshTokenRepository.detachReplacedLinks(any(), any())).thenReturn(3);
        when(refreshTokenRepository.purgeStaleTokens(any(), any())).thenReturn(42);

        scheduler.purgeStaleRefreshTokens();

        var inOrder = inOrder(refreshTokenRepository);
        inOrder.verify(refreshTokenRepository).detachReplacedLinks(any(), any());
        inOrder.verify(refreshTokenRepository).purgeStaleTokens(any(), any());
    }

    @Test
    void purge_usesOneDayRetentionForExpiredAndThirtyDaysForRevoked() {
        when(refreshTokenRepository.detachReplacedLinks(any(), any())).thenReturn(0);
        when(refreshTokenRepository.purgeStaleTokens(any(), any())).thenReturn(0);

        scheduler.purgeStaleRefreshTokens();

        ArgumentCaptor<LocalDateTime> expiresCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> revokedCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        inOrder(refreshTokenRepository)
                .verify(refreshTokenRepository)
                .detachReplacedLinks(expiresCaptor.capture(), revokedCaptor.capture());

        assertWithinMinutes(expiresCaptor.getValue(), LocalDateTime.now().minusDays(1), 5);
        assertWithinMinutes(revokedCaptor.getValue(), LocalDateTime.now().minusDays(30), 5);
    }

    private void assertWithinMinutes(LocalDateTime actual, LocalDateTime expected, int toleranceMinutes) {
        Duration diff = Duration.between(expected, actual).abs();
        assertTrue(diff.toMinutes() <= toleranceMinutes,
                "expected " + expected + " within " + toleranceMinutes + "min, got " + actual);
    }
}