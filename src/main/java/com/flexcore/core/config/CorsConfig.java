package com.flexcore.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Allows the standalone frontend (served from any origin, including a local file or a
 * dev server on a different port) to call this API from the browser. Without this,
 * fetch() calls from flexcore-login.html fail CORS preflight even though the backend
 * itself is reachable.
 * <p>
 * {@code allowedOriginPatterns("*")} is fine for a portfolio/demo API with no cookies
 * involved (auth is a Bearer token in a header, not a session cookie) — tighten this to
 * a specific origin list before using real user data.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}