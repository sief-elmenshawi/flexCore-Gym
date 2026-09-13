package com.flexcore.auth.scheduler;

import com.flexcore.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Nightly housekeeping for the refresh-token table, which grows on every login and
 * every rotation. Fully expired tokens are dead weight and are dropped quickly; revoked
 * (rotated/revoked) tokens are kept a little longer to keep rotation chains intact and
 * to retain a short forensic window around replay detection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenPurgeScheduler {

    private static final int EXPIRED_RETENTION_DAYS = 1;
    private static final int REVOKED_RETENTION_DAYS = 30;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.jobs.refresh-token-purge-cron:0 0 3 * * *}")
    @Transactional
    public void purgeStaleRefreshTokens() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresBefore = now.minusDays(EXPIRED_RETENTION_DAYS);
        LocalDateTime revokedBefore = now.minusDays(REVOKED_RETENTION_DAYS);
        int detached = refreshTokenRepository.detachReplacedLinks(expiresBefore, revokedBefore);
        int purged = refreshTokenRepository.purgeStaleTokens(expiresBefore, revokedBefore);
        if (purged > 0) {
            log.info("Refresh-token purge: {} rotation link(s) detached, {} stale token(s) removed",
                    detached, purged);
        }
    }
}