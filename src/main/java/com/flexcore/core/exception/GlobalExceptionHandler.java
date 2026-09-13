package com.flexcore.core.exception;

import com.flexcore.core.dto.response.ErrorResponse;
import com.flexcore.core.exception.TooManyRequestsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public static final String MSG_BAD_CREDENTIALS = "error.bad-credentials";
    public static final String MSG_ACCESS_DENIED = "error.access-denied";
    public static final String MSG_VALIDATION_FAILED = "error.validation-failed";
    public static final String MSG_PARAM_INVALID = "error.param-invalid";
    public static final String MSG_BODY_MALFORMED = "error.body-malformed";
    public static final String MSG_BODY_INVALID_ENUM = "error.body-invalid-enum";
    public static final String MSG_BODY_INVALID_FORMAT = "error.body-invalid-format";
    public static final String MSG_DATA_INTEGRITY = "error.data-integrity";
    public static final String MSG_RESOURCE_NOT_FOUND = "error.resource.notfound";
    public static final String MSG_INTERNAL = "error.internal";
    public static final String MSG_METHOD_NOT_ALLOWED = "error.method-not-allowed";
    public static final String MSG_MEDIA_TYPE_UNSUPPORTED = "error.media-type-unsupported";
    public static final String MSG_PARAM_MISSING = "error.param-missing";

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public GlobalExceptionHandler(MessageSource messageSource, LocaleResolver localeResolver) {
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getCode(), ex.getArgs(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getArgs(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getArgs(), request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, MSG_BAD_CREDENTIALS, request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getCode(), ex.getArgs(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, MSG_ACCESS_DENIED, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        Locale locale = localeResolver.resolveLocale(request);
        String resolved = messageSource.getMessage(MSG_VALIDATION_FAILED, null, MSG_VALIDATION_FAILED, locale);
        ErrorResponse body = ErrorResponse.validation(HttpStatus.BAD_REQUEST,
                MSG_VALIDATION_FAILED, resolved, request.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleConstraintViolation(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, MSG_PARAM_INVALID, request, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (ex.getRootCause() instanceof InvalidFormatException invalid) {
            log.warn("Malformed request body on {}: {}", request.getRequestURI(), invalid.getOriginalMessage());
            String field = "body";
            if (!invalid.getPath().isEmpty()) {
                var ref = invalid.getPath().get(invalid.getPath().size() - 1);
                field = ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]";
            }
            String value = String.valueOf(invalid.getValue());
            Class<?> targetType = invalid.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                String allowed = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                return build(HttpStatus.BAD_REQUEST, MSG_BODY_INVALID_ENUM, request, value, field, allowed);
            }
            String expected = targetType != null ? targetType.getSimpleName() : "unknown";
            return build(HttpStatus.BAD_REQUEST, MSG_BODY_INVALID_FORMAT, request, value, field, expected);
        }
        log.warn("Body unreadable on {}: root={}", request.getRequestURI(),
                ex.getRootCause() != null ? ex.getRootCause().toString() : "n/a");
        return build(HttpStatus.BAD_REQUEST, MSG_BODY_MALFORMED, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, MSG_DATA_INTEGRITY, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, MSG_RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, MSG_METHOD_NOT_ALLOWED, request, ex.getMethod());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MSG_MEDIA_TYPE_UNSUPPORTED, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, MSG_PARAM_MISSING, request, ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, MSG_PARAM_INVALID, request, ex.getName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, MSG_INTERNAL, request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String messageKey, Object[] args, HttpServletRequest request) {
        Locale locale = localeResolver.resolveLocale(request);
        Object[] rawArgs = Arrays.stream(args)
                .map(arg -> arg instanceof Number ? arg.toString() : arg)
                .toArray();
        String resolved = messageSource.getMessage(messageKey, rawArgs, messageKey, locale);
        return ResponseEntity.status(status).body(ErrorResponse.of(status, messageKey, resolved, request.getRequestURI(), rawArgs));
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String messageKey, HttpServletRequest request, Object... args) {
        return build(status, messageKey, args, request);
    }
}
