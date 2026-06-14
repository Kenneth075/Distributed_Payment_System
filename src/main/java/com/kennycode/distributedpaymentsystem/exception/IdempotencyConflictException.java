package com.kennycode.distributedpaymentsystem.exception;

/**
 * Thrown when an idempotency key is reused with a DIFFERENT request body.
 * This is a client error — they must either use the original response
 * or generate a new idempotency key.
 * Maps to HTTP 409 Conflict.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key already used with different request parameters: " + idempotencyKey);
    }
}
