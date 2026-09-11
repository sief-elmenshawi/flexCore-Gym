package com.flexcore.auth.service;

import com.flexcore.auth.entity.RefreshToken;
import com.flexcore.auth.repository.RefreshTokenRepository;
import com.flexcore.core.exception.InvalidRefreshTokenException;
import com.flexcore.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    public record TokenPair(String rawToken, User user) {
    }

    /**
     * Issues a new opaque refresh token for the given user. Only the SHA-256 hash of the
     * token is persisted, so a database leak never exposes usable tokens, and rotation
     * records a revocation chain for replay detection.
     */
    @Transactional
    public TokenPair issue(Long userId) {
        String raw = generateRaw();
        RefreshToken entity = RefreshToken.builder()
                .tokenHash(sha256(raw))
                .expiresAt(now().plusNanos(refreshTokenExpirationMs * 1_000_000))
                .createdDate(now())
                .build();
        entity.setUser(User.builder().id(userId).build());
        refreshTokenRepository.save(entity);
        return new TokenPair(raw, entity.getUser());
    }

    /**
     * Rotates the refresh token: validates the presented token, revokes it, and issues a
     * new one in its place. Returns the new raw token (the old one is unusable afterwards).
     *
     * @throws InvalidRefreshTokenException if the token is unknown, expired, revoked, or was
     *                                      already rotated. Presenting an already-rotated token
     *                                      triggers a replay signature: every active token for
     *                                      that user is revoked (family revocation).
     */
    @Transactional
    public TokenPair rotate(String rawToken) {
        String hash = sha256(rawToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException());

        if (stored.isExpired() || stored.isRevoked()) {
            throw new InvalidRefreshTokenException();
        }

        if (stored.getReplacedByToken() != null) {
            log.warn("Reuse of rotated refresh token for user {}; revoking token family", stored.getUser().getId());
            refreshTokenRepository.revokeAllActiveForUser(stored.getUser().getId());
            throw new InvalidRefreshTokenException();
        }

        String newRaw = generateRaw();
        RefreshToken replacement = RefreshToken.builder()
                .tokenHash(sha256(newRaw))
                .expiresAt(now().plusNanos(refreshTokenExpirationMs * 1_000_000))
                .createdDate(now())
                .build();
        replacement.setUser(stored.getUser());

        stored.setRevokedAt(now());
        stored.setReplacedByToken(replacement);

        refreshTokenRepository.save(replacement);
        refreshTokenRepository.save(stored);

        return new TokenPair(newRaw, stored.getUser());
    }

    @Transactional
    public void revoke(String rawToken) {
        String hash = sha256(rawToken);
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(hash);
        stored.ifPresent(token -> {
            token.setRevokedAt(now());
            refreshTokenRepository.save(token);
        });
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String generateRaw() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}