package com.kennycode.distributedpaymentsystem.repository;

import com.kennycode.distributedpaymentsystem.model.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findByPaymentIdOrderByOccurredAtAsc(UUID paymentId);
}
