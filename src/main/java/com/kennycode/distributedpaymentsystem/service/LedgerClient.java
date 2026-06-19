package com.kennycode.distributedpaymentsystem.service;


import com.kennycode.distributedpaymentsystem.dto.*;
import com.kennycode.distributedpaymentsystem.exception.LedgerException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * HTTP client for the ledger-mock service.

 * Resilience4j layers (applied in this order, outermost → innermost):

 *   TimeLimiter  — hard deadline per attempt (default 2s).
 *                  If the HTTP call takes longer, it is interrupted and counted
 *                  as a failure for the retry/circuit-breaker.

 *   Retry        — on timeout or 5xx, waits with exponential back-off + jitter
 *                  then retries (max 3 attempts by default).

 *   CircuitBreaker — tracks the rolling failure rate across all attempts.
 *                   Once it reaches the threshold it OPENS and all calls are
 *                   short-circuited immediately (no network I/O), giving the
 *                   downstream service time to recover.

 * All three names ("ledger") map to the resilience4j config in application.yml.

 * NOTE: @TimeLimiter requires the method to return CompletableFuture so Resilience4j
 * can interrupt it on the thread-pool boundary. PaymentService calls .get() to block.
 */

/**
 * Reserves funds on the ledger for the given payment.
 *
 * Resilience4j wraps this method:
 *   1. TimeLimiter — 2-second per-attempt deadline
 *   2. Retry       — up to 3 attempts with jitter back-off
 *   3. CircuitBreaker — opens after sustained failures
 *
 * @return CompletableFuture so TimeLimiter can enforce the per-attempt deadline.
 *         Callers should use .get() or .join() to retrieve the result.
 */
@Component
public class LedgerClient {

    private static final Logger log = LoggerFactory.getLogger(LedgerClient.class);

    private final RestTemplate restTemplate;
    private final String ledgerBaseUrl;

    public LedgerClient(RestTemplate restTemplate,@Value("${ledger.base-url:http://localhost:9090}") String ledgerBaseUrl) {
        this.restTemplate = restTemplate;
        this.ledgerBaseUrl = ledgerBaseUrl;
    }

    @TimeLimiter(name = "ledger", fallbackMethod = "reserveFundsTimeoutFallback")
    @Retry(name = "ledger", fallbackMethod = "reserveFundsRetryFallback")
    @CircuitBreaker(name = "ledger", fallbackMethod = "reserveFundsCircuitFallback")
    public CompletableFuture<LedgerReserveResponse>  reserveFunds(LedgerReserveRequest ledgerReserveRequest) {

        return CompletableFuture.supplyAsync(()->{
            log.info("Calling ledger-mock/reserve for paymentId={} amount={}",
                    ledgerReserveRequest.getPaymentId(), ledgerReserveRequest.getAmount());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<LedgerReserveRequest> entity = new HttpEntity<>(ledgerReserveRequest, headers);

            try{
                ResponseEntity<LedgerReserveResponse> response = restTemplate.postForEntity(
                        ledgerBaseUrl + "/ledger/reserve", entity, LedgerReserveResponse.class);

                return response.getBody();

            }catch (HttpClientErrorException ex){
                // 5xx from ledger — retryable
                log.warn("Ledger returned server error {}: {}", ex.getStatusCode(), ex.getMessage());

                throw new LedgerException(LedgerException.Reason.SERVICE_ERROR,
                        "Ledger server error: " + ex.getStatusCode(), ex);

            } catch (ResourceAccessException ex) {
                // Network timeout / connection refused — retryable
                log.warn("Ledger network error: {}", ex.getMessage());

                throw new LedgerException(LedgerException.Reason.TIMEOUT,
                        "Ledger unreachable: " + ex.getMessage(), ex);
            }


        });

    }

    // ------------------------------------------------------------------
    // Fallback methods
    //
    // Naming convention required by Resilience4j:
    //   same name as the annotated method + "FallbackMethod suffix"
    //   same return type + one extra Throwable parameter at the end
    // ------------------------------------------------------------------

    /**
     * Called when TimeLimiter fires (per-attempt deadline exceeded).
     * Resilience4j will hand off to the Retry fallback next if retries remain.
     */
    public CompletableFuture<LedgerReserveResponse>reserveFundsTimeoutFallback(LedgerReserveRequest ledgerReserveRequest, Throwable cause) {

            log.warn("Ledger call timed out for paymentId={}: {}", ledgerReserveRequest.getPaymentId(), cause.getMessage());

            return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.TIMEOUT,
                    "Ledger call timed out after deadline", cause));
    }

    /**
     * Called when all retry attempts are exhausted.
     */
    public CompletableFuture<LedgerReserveResponse> reserveFundsRetryFallback(LedgerReserveRequest ledgerReserveRequest,Throwable cause) {
        log.error("All ledger retry attempts exhausted for paymentId={}", ledgerReserveRequest.getPaymentId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.TIMEOUT,
                        "Ledger unavailable after max retries", cause));
    }


    /**
     * Fallback for all circuit-open cases on reserveFunds.
     */
    public CompletableFuture<LedgerReserveResponse> reserveFundsCircuitFallback(LedgerReserveRequest request, Throwable cause) {
        log.error("Circuit breaker OPEN — ledger call rejected for paymentId={}", request.getPaymentId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.CIRCUIT_OPEN,
                        "Ledger circuit breaker is open", cause));
    }


    // -----------------------------------------------------------------------
    // settleFunds — finalize a reservation
    // Same Resilience4j stack as reserveFunds.
    // -----------------------------------------------------------------------

    @TimeLimiter(name = "ledger", fallbackMethod = "settleFundsTimeoutFallback")
    @Retry(name = "ledger", fallbackMethod = "settleFundsRetryFallback")
    @CircuitBreaker(name = "ledger", fallbackMethod = "settleFundsCircuitFallback")
    public CompletableFuture<LedgerSettleResponse> settleFunds(LedgerSettleRequest request) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Calling ledger-mock/settle for reservationId={}", request.getReservationId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<LedgerSettleRequest> entity = new HttpEntity<>(request, headers);

            try {
                ResponseEntity<LedgerSettleResponse> response = restTemplate.postForEntity(
                        ledgerBaseUrl + "/ledger/settle", entity, LedgerSettleResponse.class);

                log.info("Ledger settlement succeeded for reservationId={}", request.getReservationId());

                return response.getBody();

            } catch (HttpServerErrorException ex) {
                log.warn("Ledger settle returned server error {}", ex.getStatusCode());

                throw new LedgerException(LedgerException.Reason.SERVICE_ERROR,
                        "Ledger settle error: " + ex.getStatusCode(), ex);

            } catch (ResourceAccessException ex) {
                log.warn("Ledger settle network error: {}", ex.getMessage());

                throw new LedgerException(LedgerException.Reason.TIMEOUT,
                        "Ledger settle unreachable: " + ex.getMessage(), ex);
            }
        });
    }

    public CompletableFuture<LedgerSettleResponse> settleFundsTimeoutFallback(LedgerSettleRequest request, Throwable cause) {
        log.info("Settle funds timeout fallback for reservationId={}", request.getReservationId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.TIMEOUT,
                "Ledger settle time out", cause));
    }

    public CompletableFuture<LedgerSettleResponse> settleFundsRetryFallback(LedgerSettleRequest request, Throwable cause) {
        log.error("Settle funds retry fallback for reservationId={}", request.getReservationId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.TIMEOUT,
                "Ledger settle unavailable after retries", cause));
    }

    public CompletableFuture<LedgerSettleResponse> settleFundsCircuitFallback(LedgerSettleRequest request, Throwable cause) {
        log.error("Circuit OPEN — ledger settle rejected for reservationId={}", request.getReservationId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.CIRCUIT_OPEN,
                "Ledger circuit breaker open", cause));
    }


    // -----------------------------------------------------------------------
    // releaseFunds — compensating transaction: reverses a reservation
    //
    // Note: Compensation uses a separate "ledgerCompensation" Resilience4j
    // instance (configured in application.yml) with more aggressive retries
    // and NO circuit breaker. Compensation MUST succeed eventually — we
    // cannot leave funds locked if settlement failed. The circuit breaker
    // is deliberately omitted here to avoid blocking compensation calls
    // when the main circuit is open.
    // -----------------------------------------------------------------------

    @TimeLimiter(name = "ledgerCompensation", fallbackMethod = "releaseFundsTimeoutFallback")
    @Retry(name = "ledgerCompensation", fallbackMethod = "releaseFundsRetryFallback")
    public CompletableFuture<LedgerReleaseResponse> releaseFunds(LedgerReleaseRequest request){

        return CompletableFuture.supplyAsync(() -> {
            log.info("COMPENSATION: calling ledger-mock /release for reservationId={}", request.getReservationId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<LedgerReleaseRequest> entity = new HttpEntity<>(request, headers);

            try {
                ResponseEntity<LedgerReleaseResponse> response = restTemplate.postForEntity(ledgerBaseUrl + "/ledger/release", entity, LedgerReleaseResponse.class);
                log.info("COMPENSATION succeeded for reservationId={}", request.getReservationId());

                return response.getBody();

            } catch (HttpServerErrorException ex) {
                log.warn("COMPENSATION: ledger release error {}", ex.getStatusCode());

                throw new LedgerException(LedgerException.Reason.SERVICE_ERROR, "Ledger release error: " + ex.getStatusCode(), ex);

            } catch (ResourceAccessException ex){
                log.warn("COMPENSATION: ledger release unreachable: {}", ex.getMessage());

                throw new LedgerException(LedgerException.Reason.TIMEOUT, "Ledger release unreachable: " + ex.getMessage(), ex);
            }
        });
    }

    public CompletableFuture<LedgerReleaseResponse> releaseFundsTimeoutFallback(LedgerReleaseRequest request, Throwable cause){
        log.error("COMPENSATION timed out for reservationId={} — will need manual intervention", request.getReservationId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.TIMEOUT,
                "Compensation timed out — manual release required for: " + request.getReservationId(), cause));
    }

    public CompletableFuture<LedgerReleaseResponse> releaseFundsRetryFallback(LedgerReleaseRequest request, Throwable cause) {
        log.error("COMPENSATION retries exhausted for reservationId={} — MANUAL INTERVENTION REQUIRED", request.getReservationId());

        return CompletableFuture.failedFuture(new LedgerException(LedgerException.Reason.TIMEOUT,
                        "Compensation failed after max retries — manual release required for: " + request.getReservationId(), cause));
    }
}
