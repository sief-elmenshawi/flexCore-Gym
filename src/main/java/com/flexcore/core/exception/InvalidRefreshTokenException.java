package com.flexcore.core.exception;

/**
 * Thrown when a presented refresh token is unknown, expired, revoked or already rotated.
 * Mapped to HTTP 401 with a dedicated message in {@link GlobalExceptionHandler}.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("error.auth.invalid-refresh-token");
    }

    public InvalidRefreshTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}