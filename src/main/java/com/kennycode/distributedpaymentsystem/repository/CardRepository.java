package com.kennycode.distributedpaymentsystem.repository;

import com.kennycode.distributedpaymentsystem.model.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CardRepository extends JpaRepository<Card, UUID> {
    Optional<Card> findByCardNumber(String cardNumber);
}
