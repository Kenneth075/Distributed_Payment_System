package com.kennycode.distributedpaymentsystem.dto;

import java.util.UUID;

public class LedgerReleaseRequest {
    private String reservationId;
    private UUID paymentId;
    private String reason;

    public LedgerReleaseRequest(String reservationId, UUID paymentId, String reason) {
        this.reservationId = reservationId;
        this.paymentId = paymentId;
        this.reason = reason;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }
}
