package com.banka1.stock_service.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

/**
 * Preserves explicit HTTP statuses and messages raised inside stock-service.
 *
 * <p>Also captures Spring Security authorization failures so a missing role
 * (e.g. on {@code /admin/stocks/refresh-all}) returns a clean HTTP 403 instead
 * of falling through to the generic 500 path. Without this mapping the
 * frontend would only see "Serverska greska" and could not distinguish a
 * legitimate auth problem from an actual bug -- which is what GHI #199
 * surfaced when refresh-all was invoked with an AGENT/BASIC token.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StockServiceResponseStatusExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<StockServiceErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode statusCode = exception.getStatusCode();
        String error = statusCode instanceof HttpStatus httpStatus
                ? httpStatus.getReasonPhrase()
                : statusCode.toString();
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? error
                : exception.getReason();

        StockServiceErrorResponse response = new StockServiceErrorResponse(
                OffsetDateTime.now(),
                statusCode.value(),
                error,
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(statusCode).body(response);
    }

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    public ResponseEntity<StockServiceErrorResponse> handleAuthorizationDenied(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        StockServiceErrorResponse response = new StockServiceErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Nedovoljne privilegije za ovu akciju.",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }
}

record StockServiceErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
