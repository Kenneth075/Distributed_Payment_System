package com.kennycode.distributedpaymentsystem.exception;

/**
 * Thrown when the ledger-mock call fails permanently —
 * i.e. after all retries are exhausted OR the circuit breaker is open.

 * This exception is caught by PaymentService and triggers Phase 3
 * saga compensation (releasing any partially reserved funds).

 * It is NOT mapped to an HTTP 5xx directly; the payment will be marked
 * COMPENSATED and the client receives a structured decline response.
 */
public class LedgerException extends RuntimeException {

    public enum Reason{
        TIMEOUT,           // all retry attempts timed out
        CIRCUIT_OPEN,     // circuit breaker is open, calls not attempted
        SERVICE_ERROR     // ledger returned a non-2xx response
    }

    private final Reason reason;

    public LedgerException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    public LedgerException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
