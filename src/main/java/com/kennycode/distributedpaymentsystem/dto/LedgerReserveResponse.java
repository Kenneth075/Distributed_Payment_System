package com.kennycode.distributedpaymentsystem.dto;


/**
 * Response from the ledger-mock service after a successful fund reservation.

 * {
 *   "reservationId": "RES-ABC123",
 *   "status":        "RESERVED"
 * }

 * The reservationId is stored on the Payment entity and is used in
 * Saga compensation to release the funds if settlement fails.
 */
public class LedgerReserveResponse {

    private String reservationId;
    private String status;

    public LedgerReserveResponse(String reservationId, String status) {
        this.reservationId = reservationId;
        this.status = status;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
