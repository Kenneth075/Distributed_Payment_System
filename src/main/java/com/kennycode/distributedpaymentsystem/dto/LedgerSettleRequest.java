package com.kennycode.distributedpaymentsystem.dto;

import java.math.BigDecimal;
import java.util.UUID;

public class LedgerSettleRequest {
    private String reservationId;
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;

    public LedgerSettleRequest(String reservationId, UUID paymentId, BigDecimal amount, String currency) {
        this.reservationId = reservationId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
