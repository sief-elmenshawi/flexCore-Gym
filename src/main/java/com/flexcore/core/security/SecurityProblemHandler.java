package com.flexcore.core.security;

import com.flexcore.core.dto.response.ErrorResponse;
import com.flexcore.core.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import java.io.IOException;

@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String MSG_AUTH_REQUIRED = "error.auth-required";

    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public SecurityProblemHandler(tools.jackson.databind.ObjectMapper objectMapper,
                                  MessageSource messageSource,
                                  LocaleResolver localeResolver) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeError(request, response, HttpStatus.UNAUTHORIZED, MSG_AUTH_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, GlobalExceptionHandler.MSG_ACCESS_DENIED);
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String code) throws IOException {
        var locale = localeResolver.resolveLocale(request);
        String message = messageSource.getMessage(code, null, code, locale);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                ErrorResponse.of(status, code, message, request.getRequestURI()));
    }
}
