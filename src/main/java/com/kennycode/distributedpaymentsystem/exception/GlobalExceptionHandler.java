package com.kennycode.distributedpaymentsystem.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler — catches all exceptions thrown from controllers
 * and returns a consistent JSON error shape to the client.

 * Error response shape:
 * {
 *   "timestamp": "...",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "...",
 *   "fieldErrors": { "field": "reason" }  // only on validation failures
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler{

    // -----------------------------------------------------------------------
    // 400 — Validation failures (@Valid on request body)
    // -----------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    // -----------------------------------------------------------------------
    // 400 — Missing required header (e.g. Idempotency-Key)
    // -----------------------------------------------------------------------
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Required header is missing: " + ex.getHeaderName(), null);
    }

    // -----------------------------------------------------------------------
    // 404 — Card not found
    // -----------------------------------------------------------------------
    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCardNotFound(CardNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // -----------------------------------------------------------------------
    // 404 — Payment not found
    // -----------------------------------------------------------------------
    @ExceptionHandler(PaymentStateException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentNotFound(PaymentStateException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // -----------------------------------------------------------------------
    // 409 — Payment is in wrong state for requested operation
    // -----------------------------------------------------------------------
    @ExceptionHandler(PaymentStateException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentState(PaymentStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // -----------------------------------------------------------------------
    // 409 — Idempotency key reused with different params
    // -----------------------------------------------------------------------
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(IdempotencyConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // -----------------------------------------------------------------------
    // 500 — Catch-all for unexpected errors
    // -----------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Log internally but don't expose stack trace to caller
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again.", null);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status,
                                                              String message,
                                                              Map<String, String> fieldErrors) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }
        return ResponseEntity.status(status).body(body);
    }
}
