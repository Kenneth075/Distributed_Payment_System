package com.kennycode.distributedpaymentsystem.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Client-supplied key that guarantees exactly-once processing.
     * If the same key arrives twice, we return the original result
     * without re-processing the payment.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private Card card;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency",nullable = false, length = 3)
    private String currency = "NGN";

    @Column(name = "merchant_id", nullable = false)
    private String merchantId;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "auth_code")
    private String authCode;

    @Column(name = "decline_reason")
    private String declineReason;

    /**
     * Token returned by ledger-mock when funds are successfully reserved.
     * Stored so Saga compensation can release them if settlement fails.
     */
    @Column(name = "ledger_reservation_id")
    private String ledgerReservationId;


    /**
     * Running count of ledger call attempts — useful for observability dashboards
     * and debugging retry storms.
     */
    @Column(name = "ledger_attempts")
    private int ledgerAttempts = 0;

    @Column(name = "settled_at")
    private OffsetDateTime settledAt;

    @Column(name = "compensated_at")
    private OffsetDateTime compensatedAt;

    @Column(name = "compensation_reason")
    private String compensationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


    @PrePersist
    public void perPersist(){
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void perUpdate(){
        this.updatedAt = OffsetDateTime.now();
    }



    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }



    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }



    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
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



    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }



    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
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



    public String getLedgerReservationId() {
        return ledgerReservationId;
    }

    public void setLedgerReservationId(String ledgerReservationId) {
        this.ledgerReservationId = ledgerReservationId;
    }


    public int getLedgerAttempts() {
        return ledgerAttempts;
    }

    public void setLedgerAttempts(int ledgerAttempts) {
        this.ledgerAttempts = ledgerAttempts;
    }



    public OffsetDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(OffsetDateTime settledAt) {
        this.settledAt = settledAt;
    }



    public OffsetDateTime getCompensatedAt() {
        return compensatedAt;
    }

    public void setCompensatedAt(OffsetDateTime compensatedAt) {
        this.compensatedAt = compensatedAt;
    }


    public String getCompensationReason() {
        return compensationReason;
    }

    public void setCompensationReason(String compensationReason) {
        this.compensationReason = compensationReason;
    }



    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }



    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }


}
