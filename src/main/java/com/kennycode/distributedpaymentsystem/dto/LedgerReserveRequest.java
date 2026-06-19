package com.kennycode.distributedpaymentsystem.dto;

import java.math.BigDecimal;
import java.util.UUID;


/**
 * Payload sent to the ledger-mock service to reserve funds.

 * POST /ledger/reserve
 * {
 *   "paymentId": "...",
 *   "cardId":    "...",
 *   "amount":    150.00,
 *   "currency":  "USD"
 * }
 */

public class LedgerReserveRequest {

    private UUID paymentId;
    private UUID cardId;
    private BigDecimal amount;
    private String currency;


    public LedgerReserveRequest(UUID paymentId, UUID cardId, BigDecimal amount, String currency) {
        this.paymentId = paymentId;
        this.cardId = cardId;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
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
