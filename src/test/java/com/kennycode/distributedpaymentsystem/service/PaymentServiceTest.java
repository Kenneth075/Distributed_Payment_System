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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


/**
 * Unit tests for PaymentService.

 * All dependencies are mocked — no Spring context, no database.
 * These run in milliseconds and test business logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private PaymentEventRepository eventRepository;

    @Mock
    private LedgerClient ledgerClient;

    @InjectMocks
    private PaymentService paymentService;

    // Test data
    private static final String IDEMPOTENCY_KEY = "test-idem-key-001";
    private static final UUID CARD_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");

    private Card activeCard;
    private AuthorizeRequest validRequest;

    @BeforeEach
    void setUp() {
        activeCard = new Card();
        activeCard.setId(CARD_ID);
        activeCard.setCardNumber("4111-XXXX-XXXX-1111");
        activeCard.setCardHolderName("Ken Demo");
        activeCard.setDailyLimit(new BigDecimal("5000.00"));
        activeCard.setIsActive(true);

        validRequest = new AuthorizeRequest();
        validRequest.setCardId(CARD_ID.toString());
        validRequest.setAmount(new BigDecimal("150.00"));
        validRequest.setCurrency("NGN");
        validRequest.setMerchantId("merchant-001");
        validRequest.setDescription("Test order");

        // Default: no existing payment with this idempotency key
        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        // Default: save returns the payment passed in (simulates DB persist)
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(invocation -> {
                    Payment p = invocation.getArgument(0);
                    if (p.getId() == null) p.setId(UUID.randomUUID());
                    if (p.getCreatedAt() == null) p.setCreatedAt(OffsetDateTime.now());
                    if (p.getUpdatedAt() == null) p.setUpdatedAt(OffsetDateTime.now());
                    return p;
                });

        // Default: ledger reserves successfully
        LedgerReserveResponse ledgerOk = new LedgerReserveResponse("RES-TESTOK", "RESERVED");
        when(ledgerClient.reserveFunds(any(LedgerReserveRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ledgerOk));
    }

    // -----------------------------------------------------------------------
    // Happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("New payment within limits → AUTHORIZED with auth code")
    void authorize_validRequest_returnsAuthorized() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any()))
                .thenReturn(BigDecimal.ZERO); // no spend today

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(response.getAuthCode()).startsWith("AUTH-");
        assertThat(response.getDeclineReason()).isNull();
        assertThat(response.isIdempotentReplay()).isFalse();
        assertThat(response.getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Authorized payment persists AUTHORIZED status to DB")
    void authorize_validRequest_savesCorrectStatus() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any()))
                .thenReturn(BigDecimal.ZERO);

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        // Capture all save() calls and verify the last one has AUTHORIZED status
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(captor.capture());

        //Payment lastSaved = captor.getAllValues().getLast();
        Payment lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(lastSaved.getAuthCode()).isNotNull();
    }


    // -----------------------------------------------------------------------
    // Idempotency
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Duplicate idempotency key → returns original result, no re-processing")
    void authorize_duplicateKey_returnsIdempotentReplay() {
        Payment existing = new Payment();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey(IDEMPOTENCY_KEY);
        existing.setCard(activeCard);
        existing.setAmount(new BigDecimal("150.00"));
        existing.setCurrency("USD");
        existing.setMerchantId("merchant-001");
        existing.setStatus(PaymentStatus.AUTHORIZED);
        existing.setAuthCode("AUTH-ABCD1234");
        existing.setCreatedAt(OffsetDateTime.now());

        when(paymentRepository.findByIdempotencyKey(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(existing));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.isIdempotentReplay()).isTrue();
        assertThat(response.getAuthCode()).isEqualTo("AUTH-ABCD1234");

        // Card lookup and limit check must NOT be called — we short-circuit early
        verify(cardRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Card validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Non-existent card → throws CardNotFoundException")
    void authorize_unknownCard_throwsCardNotFoundException() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest))
                .isInstanceOf(CardNotFoundException.class)
                .hasMessageContaining(CARD_ID.toString());
    }

    @Test
    @DisplayName("Inactive card → DECLINED with CARD_INACTIVE reason")
    void authorize_inactiveCard_returnsDeclined() {
        activeCard.setIsActive(false);
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(response.getDeclineReason()).containsIgnoringCase("not active");
    }

    @Test
    @DisplayName("Invalid UUID as cardId → throws CardNotFoundException")
    void authorize_invalidCardIdFormat_throwsCardNotFoundException() {
        validRequest.setCardId("not-a-uuid");

        assertThatThrownBy(() -> paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest))
                .isInstanceOf(CardNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // Daily limit enforcement
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Amount exactly at remaining limit → AUTHORIZED")
    void authorize_amountEqualsRemainingLimit_authorized() {
        // Card limit = 5000, spent = 4850, amount = 150 → exactly at limit
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any()))
                .thenReturn(new BigDecimal("4850.00"));

        validRequest.setAmount(new BigDecimal("150.00"));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    @Test
    @DisplayName("Amount exceeds remaining daily limit → DECLINED with DAILY_LIMIT_EXCEEDED")
    void authorize_exceedsDailyLimit_returnsDeclined() {
        // Card limit = 5000, spent = 4900, amount = 200 → over limit
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any()))
                .thenReturn(new BigDecimal("4900.00"));

        validRequest.setAmount(new BigDecimal("200.00"));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(response.getDeclineReason()).containsIgnoringCase("limit");
    }

    @Test
    @DisplayName("Fresh card with zero spend → AUTHORIZED")
    void authorize_noSpendToday_authorized() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any()))
                .thenReturn(BigDecimal.ZERO);

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // Audit events
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Successful authorization → saves PaymentEvent to audit log")
    void authorize_success_savesAuditEvent() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(eq(CARD_ID), anyList(), any()))
                .thenReturn(BigDecimal.ZERO);

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventRepository, atLeastOnce()).save(eventCaptor.capture());

        PaymentEvent event = eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("AUTHORIZED");
    }

    @Test
    @DisplayName("Declined payment → saves PaymentEvent to audit log")
    void authorize_declined_savesAuditEvent() {
        activeCard.setIsActive(false);
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventRepository, atLeastOnce()).save(eventCaptor.capture());

        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("CARD_INACTIVE");
    }

    // -----------------------------------------------------------------------
    // Phase 2 — Ledger integration
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Ledger success → reservationId stored on payment, LEDGER_RESERVED event saved")
    void authorize_ledgerSuccess_reservationIdPersisted() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any())).thenReturn(BigDecimal.ZERO);

        LedgerReserveResponse ledgerResp = new LedgerReserveResponse("RES-ABC99999", "RESERVED");
        when(ledgerClient.reserveFunds(any()))
                .thenReturn(CompletableFuture.completedFuture(ledgerResp));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);

        // Verify LEDGER_RESERVED event was recorded
        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventRepository, atLeastOnce()).save(eventCaptor.capture());
        boolean hasLedgerEvent = eventCaptor.getAllValues().stream()
                .anyMatch(e -> "LEDGER_RESERVED".equals(e.getEventType()));
        assertThat(hasLedgerEvent).isTrue();
    }

    @Test
    @DisplayName("Ledger failure (timeout) → payment DECLINED with LEDGER_UNAVAILABLE")
    void authorize_ledgerTimeout_returnsDeclined() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any())).thenReturn(BigDecimal.ZERO);

        LedgerException timeoutEx = new LedgerException("Ledger unavailable after max retries", LedgerException.Reason.TIMEOUT);
        when(ledgerClient.reserveFunds(any()))
                .thenReturn(CompletableFuture.failedFuture(timeoutEx));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(response.getDeclineReason()).containsIgnoringCase("could not be processed");
    }

    @Test
    @DisplayName("Ledger failure (circuit open) → payment DECLINED")
    void authorize_ledgerCircuitOpen_returnsDeclined() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any())).thenReturn(BigDecimal.ZERO);

        LedgerException circuitEx = new LedgerException("Ledger circuit breaker is open", LedgerException.Reason.CIRCUIT_OPEN);
        when(ledgerClient.reserveFunds(any()))
                .thenReturn(CompletableFuture.failedFuture(circuitEx));

        AuthorizeResponse response = paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    }

    @Test
    @DisplayName("Ledger failure → LEDGER_FAILED audit event is saved")
    void authorize_ledgerFailure_savesLedgerFailedEvent() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any())).thenReturn(BigDecimal.ZERO);

        LedgerException ex = new LedgerException("500 from ledger", LedgerException.Reason.SERVICE_ERROR);
        when(ledgerClient.reserveFunds(any()))
                .thenReturn(CompletableFuture.failedFuture(ex));

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        ArgumentCaptor<PaymentEvent> eventCaptor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventRepository, atLeastOnce()).save(eventCaptor.capture());

        boolean hasFailedEvent = eventCaptor.getAllValues().stream()
                .anyMatch(e -> "LEDGER_FAILED".equals(e.getEventType()));
        assertThat(hasFailedEvent).isTrue();
    }

    @Test
    @DisplayName("Ledger called with correct paymentId and amount")
    void authorize_ledgerCalledWithCorrectParams() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any())).thenReturn(BigDecimal.ZERO);

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        ArgumentCaptor<LedgerReserveRequest> ledgerCaptor =
                ArgumentCaptor.forClass(LedgerReserveRequest.class);
        verify(ledgerClient).reserveFunds(ledgerCaptor.capture());

        LedgerReserveRequest captured = ledgerCaptor.getValue();
        assertThat(captured.getAmount()).isEqualByComparingTo("150.00");
        assertThat(captured.getCurrency()).isEqualTo("USD");
        assertThat(captured.getCardId()).isEqualTo(CARD_ID);
    }

    @Test
    @DisplayName("Ledger NOT called for inactive card — short-circuit before network I/O")
    void authorize_inactiveCard_ledgerNeverCalled() {
        activeCard.setIsActive(false);
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        verify(ledgerClient, never()).reserveFunds(any());
    }

    @Test
    @DisplayName("Ledger NOT called when daily limit exceeded — short-circuit before network I/O")
    void authorize_limitExceeded_ledgerNeverCalled() {
        when(cardRepository.findById(CARD_ID)).thenReturn(Optional.of(activeCard));
        when(paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
                eq(CARD_ID), anyList(), any()))
                .thenReturn(new BigDecimal("4999.00")); // only $1 remaining

        validRequest.setAmount(new BigDecimal("200.00")); // over limit

        paymentService.authorizePayment(IDEMPOTENCY_KEY, validRequest);

        verify(ledgerClient, never()).reserveFunds(any());
    }
}
