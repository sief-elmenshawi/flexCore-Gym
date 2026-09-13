package com.flexcore.auth.repository;

import com.flexcore.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken t SET t.revokedAt = CURRENT_TIMESTAMP WHERE t.user.id = :userId AND t.revokedAt IS NULL")
    int revokeAllActiveForUser(@Param("userId") Long userId);

    /**
     * Breaks rotation-chain links pointing into the purge set, so the FK on
     * {@code replaced_by_token_id} never blocks the bulk delete that follows.
     */
    @Modifying
    @Query("""
            UPDATE RefreshToken t
            SET t.replacedByToken = null
            WHERE t.replacedByToken.id IN (
                SELECT r.id FROM RefreshToken r
                WHERE r.expiresAt < :expiresBefore
                   OR (r.revokedAt IS NOT NULL AND r.revokedAt < :revokedBefore))
            """)
    int detachReplacedLinks(@Param("expiresBefore") LocalDateTime expiresBefore,
                            @Param("revokedBefore") LocalDateTime revokedBefore);

    @Modifying
    @Query("""
            DELETE FROM RefreshToken t
            WHERE t.expiresAt < :expiresBefore
               OR (t.revokedAt IS NOT NULL AND t.revokedAt < :revokedBefore)
            """)
    int purgeStaleTokens(@Param("expiresBefore") LocalDateTime expiresBefore,
                         @Param("revokedBefore") LocalDateTime revokedBefore);
}