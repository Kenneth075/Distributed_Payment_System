package com.kennycode.distributedpaymentsystem.service;

import com.kennycode.distributedpaymentsystem.dto.AuthorizeRequest;
import com.kennycode.distributedpaymentsystem.dto.AuthorizeResponse;
import com.kennycode.distributedpaymentsystem.dto.LedgerReserveRequest;
import com.kennycode.distributedpaymentsystem.dto.LedgerReserveResponse;
import com.kennycode.distributedpaymentsystem.exception.CardNotFoundException;
import com.kennycode.distributedpaymentsystem.exception.LedgerException;
import com.kennycode.distributedpaymentsystem.model.Card;
import com.kennycode.distributedpaymentsystem.model.Payment;
import com.kennycode.distributedpaymentsystem.model.PaymentEvent;
import com.kennycode.distributedpaymentsystem.model.PaymentStatus;
import com.kennycode.distributedpaymentsystem.repository.CardRepository;
import com.kennycode.distributedpaymentsystem.repository.PaymentEventRepository;
import com.kennycode.distributedpaymentsystem.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(PaymentStatus.AUTHORIZED, PaymentStatus.SETTLING, PaymentStatus.SETTLED);

    private final PaymentRepository paymentRepository;
    private final CardRepository cardRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final LedgerClient ledgerClient;


    public PaymentService(PaymentRepository paymentRepository, CardRepository cardRepository,
                          PaymentEventRepository paymentEventRepository, LedgerClient ledgerClient) {
        this.paymentRepository = paymentRepository;
        this.cardRepository = cardRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.ledgerClient = ledgerClient;
    }

    @Transactional
    public AuthorizeResponse authorizePayment(String idempotencyKey, AuthorizeRequest request) {

        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if(existing.isPresent()) {
            return toResponse(existing.get(), true);
        }

        UUID cardId;
        try {
            cardId = UUID.fromString(request.getCardId());
        }catch (IllegalArgumentException e) {
            throw new CardNotFoundException(request.getCardId());
        }

        Card card = cardRepository.findById(cardId)
                .orElseThrow(() -> new CardNotFoundException(request.getCardId()));

        Payment payment = new Payment();
        payment.setIdempotencyKey(idempotencyKey);
        payment.setCard(card);
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMerchantId(request.getMerchantId());
        payment.setDescription(request.getDescription());
        paymentRepository.save(payment);

        //Card must be active
        if(!card.getIsActive()){
            return decline(payment, "INACTIVE_CARD", "Payment decline, card is inactive.");
        }

        //Daily limit check.
//        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC)
//                .toLocalTime()
//                .atStartOfDay()
//                .atOffSet(ZoneOffset.UTC);

        OffsetDateTime startOfToday = LocalDate.now(ZoneOffset.UTC)
                .atStartOfDay(ZoneOffset.UTC)
                .toOffsetDateTime();

        BigDecimal spentToday = paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                card.getId(), ACTIVE_STATUSES, startOfToday);

        BigDecimal remainingLimit = card.getDailyLimit().subtract(spentToday);

        if(request.getAmount().compareTo(remainingLimit) > 0) {
            return decline(payment, "DAILY_LIMIT_EXCEEDED",
                    String.format("Insufficient daily limit. Requested: %.2f, Available: %.2f",
                            request.getAmount(), remainingLimit));
        }


        // ------------------------------------------------------------------
        // LedgerClient handles: retry with jitter, per-attempt timeout,
        // and circuit breaker. If all attempts fail, it throws LedgerException.
        // We record every attempt count on the payment for observability.
        // ------------------------------------------------------------------
        LedgerReserveRequest ledgerRequest = new LedgerReserveRequest(payment.getId(), card.getId(), request.getAmount(), request.getCurrency());

        LedgerReserveResponse ledgerResponse;
        try {
            ledgerResponse = ledgerClient.reserveFunds(ledgerRequest).get();

            // Track how many attempts it took (Resilience4j increments internally;
            // we read the attempt count from the event log)
            payment.setLedgerAttempts(payment.getLedgerAttempts() + 1);
            payment.setLedgerReservationId(ledgerResponse.getReservationId());

            paymentEventRepository.save(PaymentEvent.of(payment, "LEDGER_RESERVED",
                    "Reservation ID: " + ledgerResponse.getReservationId()));

        } catch (ExecutionException ex) {
            // Unwrap: CompletableFuture wraps the real cause in ExecutionException
            Throwable cause = ex.getCause();
            payment.setLedgerAttempts(payment.getLedgerAttempts() + 1);
            paymentRepository.save(payment);

            if (cause instanceof LedgerException le) {
                log.error("Ledger reservation failed for paymentId={} reason={}: {}",
                        payment.getId(), le.getReason(), le.getMessage());

                paymentEventRepository.save(PaymentEvent.of(payment, "LEDGER_FAILED",
                        le.getReason() + ": " + le.getMessage()));
            } else {
                log.error("Unexpected error during ledger call for paymentId={}", payment.getId(), cause);
                paymentEventRepository.save(PaymentEvent.of(payment, "LEDGER_FAILED",
                        "Unexpected error: " + cause.getMessage()));
            }

            // Decline the payment — compensation will handle any partial state
            return decline(payment, "LEDGER_UNAVAILABLE", "Payment could not be processed. Please try again.");

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Ledger call interrupted for paymentId={}", payment.getId());
            return decline(payment, "LEDGER_UNAVAILABLE", "Payment processing was interrupted.");
        }

        // ------------------------------------------------------------------
        // Authorize — ledger confirmed the reservation
        // ------------------------------------------------------------------
        String authCode = generateAuthCode();
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthCode(authCode);
        paymentRepository.save(payment);

        paymentEventRepository.save(PaymentEvent.of(payment, "AUTHORIZED",
                "Auth code issued: " + authCode));

        log.info("Payment AUTHORIZED: paymentId={} authCode={}", payment.getId(), authCode);

        return AuthorizeResponse.authorize(
                payment.getId(),
                idempotencyKey,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMerchantId(),
                authCode,
                payment.getCreatedAt()
        );
    }



    private AuthorizeResponse decline(Payment payment,String reason, String message) {
        payment.setStatus(PaymentStatus.DECLINED);
        payment.setDeclineReason(reason);
        paymentRepository.save(payment);

        paymentEventRepository.save(PaymentEvent.of(payment, reason, message));

        return AuthorizeResponse.declined(
                payment.getId(),
                payment.getIdempotencyKey(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMerchantId(),
                message,
                payment.getCreatedAt()
        );
    }

    private AuthorizeResponse toResponse(Payment payment, boolean isReply) {

        AuthorizeResponse response;
        if(payment.getStatus().equals(PaymentStatus.DECLINED)) {
            response = AuthorizeResponse.declined(payment.getId(), payment.getIdempotencyKey(), payment.getAmount(),
                    payment.getCurrency(), payment.getMerchantId(), payment.getDescription(), payment.getCreatedAt());
        }
        else {
            response = AuthorizeResponse.authorize(payment.getId(),payment.getIdempotencyKey(),payment.getAmount(),
                    payment.getCurrency(),payment.getMerchantId(),payment.getDescription(),payment.getCreatedAt());
        }
        response.setIdempotentReplay(isReply);
        return response;
    }

    /**
     * Generates a short alphanumeric authorization code.
     * Format: AUTH-{8 random uppercase hex chars}
     */
    private String generateAuthCode() {
        return "AUTH-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
    }
}
