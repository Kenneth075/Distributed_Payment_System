package com.kennycode.distributedpaymentsystem.exception;

import java.util.UUID;

public class PaymentStateException extends RuntimeException {
    public PaymentStateException(UUID paymentId) {

        super("Could not find payment with id: " + paymentId);
    }
}
