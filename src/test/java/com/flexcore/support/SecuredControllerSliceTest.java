package com.flexcore.support;

import com.flexcore.core.security.CustomUserPrincipal;
import com.flexcore.core.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base class for @WebMvcTest slices running the real SecurityConfiguration and
 * the real JwtAuthFilter (with a mocked JwtTokenProvider). Tests attach an
 * authenticated {@link CustomUserPrincipal} to requests explicitly via
 * {@code .with(asUser(...))}, mirroring what JwtAuthFilter produces in production
 * from the permissions embedded in the token.
 */
public abstract class SecuredControllerSliceTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JwtTokenProvider jwtTokenProvider;

    /** Request post-processor authenticating as a user with permission-code authorities. */
    protected static RequestPostProcessor asUser(long id, String roleName, String... permissions) {
        Set<SimpleGrantedAuthority> authorities = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
        CustomUserPrincipal principal = new CustomUserPrincipal(id, null, roleName);
        return SecurityMockMvcRequestPostProcessors.authentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
