package com.kennycode.distributedpaymentsystem.controller;

import com.kennycode.distributedpaymentsystem.dto.AuthorizeRequest;
import com.kennycode.distributedpaymentsystem.dto.AuthorizeResponse;
import com.kennycode.distributedpaymentsystem.repository.PaymentRepository;
import com.kennycode.distributedpaymentsystem.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.EntityResponse;

@RestController
@RequestMapping("api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }


    public ResponseEntity<AuthorizeResponse> authorize(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody AuthorizeRequest authorizeRequest){

        AuthorizeResponse response = paymentService.authorizePayment(idempotencyKey, authorizeRequest);

        // Return 200 for idempotent replays, 201 for new authorizations
        HttpStatus status = response.isIdempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);

    }
}
