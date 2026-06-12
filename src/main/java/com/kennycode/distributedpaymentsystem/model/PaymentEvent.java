package com.kennycode.distributedpaymentsystem.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /**
     * Describes what happened, e.g.:
     * VALIDATION_PASSED, CARD_INACTIVE, LIMIT_EXCEEDED,
     * LEDGER_RESERVED, LEDGER_TIMEOUT, COMPENSATION_TRIGGERED, etc.
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    public void prePersist() {
        this.occurredAt = OffsetDateTime.now();
    }

    // --- Static factory for convenience ---

    public static PaymentEvent of(Payment payment, String eventType, String details) {
        PaymentEvent event = new PaymentEvent();
        event.setPayment(payment);
        event.setEventType(eventType);
        event.setDetails(details);
        return event;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
