package com.flexcore.core.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Standard error response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "Timestamp of the error", example = "2026-08-21T10:15:30Z")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "404")
        int status,

        @Schema(description = "HTTP status reason phrase", example = "Not Found")
        String error,

        @Schema(description = "Message key the client can localize, or a ready message", example = "error.booking.duplicate")
        String message,

        @Schema(description = "Request path that caused the error", example = "/api/v1/users/99")
        String path,

        @Schema(description = "Validation errors per field")
        Map<String, String> fieldErrors,

        @Schema(description = "Arguments interpolated into the localized message")
        Object[] arguments
) {

    public static ErrorResponse of(HttpStatus httpStatus, String message, String path) {
        return new ErrorResponse(Instant.now(), httpStatus.value(), httpStatus.getReasonPhrase(), message, path, null, null);
    }

    public static ErrorResponse of(HttpStatus httpStatus, String message, String path, Object[] arguments) {
        return new ErrorResponse(Instant.now(), httpStatus.value(), httpStatus.getReasonPhrase(), message, path, null, arguments);
    }

    public static ErrorResponse validation(HttpStatus httpStatus, String message, String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), httpStatus.value(), httpStatus.getReasonPhrase(), message, path, fieldErrors, null);
    }
}
