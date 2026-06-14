package com.kennycode.distributedpaymentsystem.dto;

import com.kennycode.distributedpaymentsystem.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AuthorizeResponse {

    /*
     * Returned for both new authorizations AND idempotent replays
     * (same response shape regardless of whether this was a duplicate request).
     */
    private UUID paymentId;
    private String idempotencyKey;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private String merchantId;
    private String authCode;            // present when status = AUTHORIZED
    private String declineReason;      // present when status = DECLINED
    private boolean idempotentReplay; // true if this response was served from a previous result
    private OffsetDateTime createdAt;


    public static AuthorizeResponse authorize(UUID paymentId, String idempotencyKey, BigDecimal amount,
                                       String currency, String merchantId, String authCode, OffsetDateTime createdAt) {

        AuthorizeResponse authorizeResponse = new AuthorizeResponse();
        authorizeResponse.paymentId = paymentId;
        authorizeResponse.idempotencyKey = idempotencyKey;
        authorizeResponse.status = PaymentStatus.AUTHORIZED;
        authorizeResponse.amount = amount;
        authorizeResponse.currency = currency;
        authorizeResponse.merchantId = merchantId;
        authorizeResponse.authCode = authCode;
        authorizeResponse.createdAt = createdAt;
        return authorizeResponse;

    }

    public static AuthorizeResponse declined(UUID paymentId, String idempotencyKey, BigDecimal amount, String currency,
                                             String merchantId, String declineReason, OffsetDateTime createdAt) {
        AuthorizeResponse r = new AuthorizeResponse();
        r.paymentId = paymentId;
        r.idempotencyKey = idempotencyKey;
        r.status = PaymentStatus.DECLINED;
        r.amount = amount;
        r.currency = currency;
        r.merchantId = merchantId;
        r.declineReason = declineReason;
        r.createdAt = createdAt;
        return r;
    }

    // --- Getters and Setters ---

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
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

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(String declineReason) {
        this.declineReason = declineReason;
    }

    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    public void setIdempotentReplay(boolean idempotentReplay) {
        this.idempotentReplay = idempotentReplay;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
