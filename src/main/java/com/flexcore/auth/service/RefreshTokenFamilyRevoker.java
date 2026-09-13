package com.flexcore.auth.service;

import com.flexcore.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a user's entire refresh-token family in its own transaction.
 *
 * <p>A replay branch inside {@link RefreshTokenService#rotate(String)} must revoke every
 * active session for the user and then throw, which rolls back the surrounding transaction.
 * Running the revocation in a {@code REQUIRES_NEW} transaction keeps it committed even when
 * the caller transaction rolls back, so stolen rotated tokens are actually killed.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Revokes every active refresh token of the given user in a fresh transaction.
     *
     * @param userId the user whose active refresh tokens should be revoked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllActiveForUser(Long userId) {
        refreshTokenRepository.revokeAllActiveForUser(userId);
    }
}