package com.kennycode.distributedpaymentsystem.model;

/**
 * Represents every possible state in the payment saga lifecycle.

 * State transitions:

 *   PENDING ──► AUTHORIZED ──► SETTLING ──► SETTLED
 *      │                           │
 *      └──► DECLINED          COMPENSATED  (if settlement fails)

 * PENDING     : Payment received, validation in progress.
 * AUTHORIZED  : Card validated, funds reserved on the ledger.
 * SETTLING    : Settlement call to ledger is in progress.
 * SETTLED     : Payment fully complete. Terminal state (success).
 * DECLINED    : Validation failed (bad card, over limit, etc). Terminal state (failure).
 * COMPENSATED : Settlement failed; ledger reservation reversed. Terminal state (failure).
 */

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    SETTLING,
    SETTLED,
    DECLINED,
    COMPENSATED
}
