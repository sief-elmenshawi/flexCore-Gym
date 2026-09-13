package com.flexcore.core.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenExpirationMs;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs) {
        if (!isStrongSecret(secret)) {
            throw new IllegalStateException(
                    "app.jwt.secret is missing, too short, or still the placeholder value. "
                            + "Set a strong JWT_SECRET (>= 32 ASCII bytes) before starting the service.");
        }
        // hmacShaKeyFor throws if < 256 bits, so >= 32 ASCII bytes is also enforced here.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    private static boolean isStrongSecret(String secret) {
        return secret != null
                && secret.getBytes(StandardCharsets.UTF_8).length >= 32
                && !"change-this-secret-in-production-change-this-secret-in-production".equals(secret);
    }

    public String generateAccessToken(Long userId, String email, String roleName, Set<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", roleName)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Permission codes read from already-parsed claims. Lets callers parse the
     * token once per request and reuse the result for every claim lookup instead
     * of re-running signature verification per field.
     */
    public Set<String> extractPermissions(Claims claims) {
        Object raw = claims.get("permissions");
        if (!(raw instanceof Iterable<?> values)) {
            return Set.of();
        }
        Set<String> permissions = new HashSet<>();
        for (Object value : values) {
            if (value instanceof String code) {
                permissions.add(code);
            }
        }
        return Set.copyOf(permissions);
    }

    /** Parses and signature-verifies the token exactly once, returning its claims. */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
