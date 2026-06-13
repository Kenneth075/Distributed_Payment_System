package com.kennycode.distributedpaymentsystem.repository;

import com.kennycode.distributedpaymentsystem.model.Payment;
import com.kennycode.distributedpaymentsystem.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Idempotency check: find an existing payment by its client-supplied key.
     * Called before any processing to detect duplicate requests.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Calculates total spend on a card for today (from midnight UTC).
     * Used to enforce daily card limits.
     * Only counts AUTHORIZED, SETTLING, and SETTLED payments —
     * DECLINED and COMPENSATED do not count against the limit.
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM Payment p
            WHERE p.card.id = :cardId
              AND p.status IN (:statuses)
              AND p.createdAt >= :since
            """)
    BigDecimal sumAmountByCardIdAndStatusInAndCreatedAtAfter(
            @Param("cardId") UUID cardId,
            @Param("statuses") java.util.List<PaymentStatus> statuses,
            @Param("since") OffsetDateTime since
    );
}
