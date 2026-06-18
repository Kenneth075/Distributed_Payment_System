package com.kennycode.distributedpaymentsystem.service;

import com.kennycode.distributedpaymentsystem.dto.AuthorizeRequest;
import com.kennycode.distributedpaymentsystem.dto.AuthorizeResponse;
import com.kennycode.distributedpaymentsystem.exception.CardNotFoundException;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

//    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
//
//    private static final List<PaymentStatus> ACTIVE_STATUSES = List.of(PaymentStatus.AUTHORIZED, PaymentStatus.SETTLING, PaymentStatus.SETTLED);
//
//    private final PaymentRepository paymentRepository;
//    private final CardRepository cardRepository;
//    private final PaymentEventRepository paymentEventRepository;
//
//
//    public PaymentService(PaymentRepository paymentRepository, CardRepository cardRepository, PaymentEventRepository paymentEventRepository) {
//        this.paymentRepository = paymentRepository;
//        this.cardRepository = cardRepository;
//        this.paymentEventRepository = paymentEventRepository;
//    }
//
//    @Transactional
//    public AuthorizeResponse authorizePayment(String idempotencyKey, AuthorizeRequest request) {
//
//        var existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
//        if(existing.isPresent()) {
//            return toResponse(existing.get(), true);
//        }
//
//        UUID cardId;
//        try {
//            cardId = UUID.fromString(request.getCardId());
//        }catch (IllegalArgumentException e) {
//            throw new CardNotFoundException(request.getCardId());
//        }
//
//        Card card = cardRepository.findById(cardId)
//                .orElseThrow(() -> new CardNotFoundException(request.getCardId()));
//
//        Payment payment = new Payment();
//        payment.setIdempotencyKey(idempotencyKey);
//        payment.setCard(card);
//        payment.setAmount(request.getAmount());
//        payment.setCurrency(request.getCurrency());
//        payment.setStatus(PaymentStatus.PENDING);
//        payment.setMerchantId(request.getMerchantId());
//        payment.setDescription(request.getDescription());
//        paymentRepository.save(payment);
//
//        //Card must be active
//        if(!card.getIsActive()){
//            return decline(payment, "INACTIVE_CARD", "Payment decline, card is inactive.");
//        }
//
//        //Daily limit check.
//        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC)
//                .toLocalTime()
//                .atStartOfDay()
//                .atOffSet(ZoneOffset.UTC);
//
//        BigDecimal spentToday = paymentRepository.sumAmountByCardIdAndStatusInAndCreatedAtAfter(
//                card.getId(), ACTIVE_STATUSES, startOfToday);
//
//        BigDecimal remainingLimit = card.getDailyLimit().subtract(spentToday);
//
//        if(request.getAmount().compareTo(remainingLimit) > 0) {
//            return decline(payment, "DAILY_LIMIT_EXCEEDED",
//                    String.format("Insufficient daily limit. Requested: %.2f, Available: %.2f",
//                            request.getAmount(), remainingLimit));
//        }
//
//    }
//
//    private AuthorizeResponse decline(Payment payment,String reason, String message) {
//        payment.setStatus(PaymentStatus.DECLINED);
//        payment.setDeclineReason(reason);
//        paymentRepository.save(payment);
//
//        paymentEventRepository.save(PaymentEvent.of(payment, reason, message));
//
//        return AuthorizeResponse.declined(
//                payment.getId(),
//                payment.getIdempotencyKey(),
//                payment.getAmount(),
//                payment.getCurrency(),
//                payment.getMerchantId(),
//                message,
//                payment.getCreatedAt()
//        );
//    }
//
//    private AuthorizeResponse toResponse(Payment payment, boolean isReply) {
//
//        AuthorizeResponse response;
//        if(payment.getStatus().equals(PaymentStatus.DECLINED)) {
//            response = AuthorizeResponse.declined(payment.getId(), payment.getIdempotencyKey(), payment.getAmount(),
//                    payment.getCurrency(), payment.getMerchantId(), payment.getDescription(), payment.getCreatedAt());
//        }
//        else {
//            response = AuthorizeResponse.authorize(payment.getId(),payment.getIdempotencyKey(),payment.getAmount(),
//                    payment.getCurrency(),payment.getMerchantId(),payment.getDescription(),payment.getCreatedAt());
//        }
//        response.setIdempotentReplay(isReply);
//        return response;
//    }
//
//    /**
//     * Generates a short alphanumeric authorization code.
//     * Format: AUTH-{8 random uppercase hex chars}
//     */
//    private String generateAuthCode() {
//        return "AUTH-" + UUID.randomUUID().toString().replace("-", "")
//                .substring(0, 8).toUpperCase();
//    }
}
