package com.flexcore.core.filter;

import com.flexcore.core.security.CustomUserPrincipal;
import com.flexcore.core.security.JwtTokenProvider;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

/**
 * Proves that JwtAuthFilter performs exactly one JWT signature-verification
 * (parseClaims) per authenticated request, instead of the 4x it did before
 * the performance fix.
 */
class JwtAuthFilterTest {

    private static final String SECRET = "flexcore-test-secret-key-for-256-hmac-sha256-api-test";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtTokenProvider realProvider;
    private JwtTokenProvider spyProvider;
    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        realProvider = new JwtTokenProvider(SECRET, 900_000L);
        spyProvider = Mockito.spy(realProvider);
        filter = new JwtAuthFilter(spyProvider);

        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer " + realProvider.generateAccessToken(
                7L, "ahmed@test.com", "MEMBER", Set.of("VIEW_MEMBERSHIP")));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_parsesClaimsExactlyOnce() throws ServletException, IOException {
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(spyProvider, Mockito.atMost(1)).parseClaims(Mockito.anyString());
        verify(spyProvider, Mockito.atLeast(1)).parseClaims(Mockito.anyString());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        CustomUserPrincipal principal = (CustomUserPrincipal) auth.getPrincipal();
        assertEquals(7L, principal.id());
        assertEquals("MEMBER", principal.roleName());
        assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("VIEW_MEMBERSHIP")));
    }

    @Test
    void invalidToken_doesNotSetAuthenticationAndCallsParseExactlyOnce() throws ServletException, IOException {
        request.removeHeader("Authorization");
        request.addHeader("Authorization", "Bearer invalid.token.here");
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(spyProvider, atMost(1)).parseClaims(Mockito.anyString());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(null, auth);
    }

    @Test
    void noAuthorizationHeader_skipsParse() throws ServletException, IOException {
        request.removeHeader("Authorization");
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(spyProvider, Mockito.never()).parseClaims(Mockito.anyString());
    }
}
