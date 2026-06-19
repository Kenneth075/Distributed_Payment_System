package com.kennycode.distributedpaymentsystem.dto;

import java.util.UUID;

public class LedgerReleaseResponse {
    private String reservationId;
    private String reason;

    public LedgerReleaseResponse(String reservationId, String reason) {
        this.reservationId = reservationId;
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
}
