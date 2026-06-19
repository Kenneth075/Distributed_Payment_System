package com.kennycode.distributedpaymentsystem.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;


/**
 * Application-level Spring beans.

 * RestTemplate is configured here with connect and read timeouts.
 * These are the socket-level timeouts — a separate, tighter per-call
 * deadline is enforced by Resilience4j's TimeLimiter on LedgerClient.

 * Having both means:
 *   - TimeLimiter: "this single attempt must complete within 2s"
 *   - RestTemplate socket timeout: safety net so a stalled TCP connection
 *     doesn't hold a thread forever if TimeLimiter misses for any reason.
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3)) //TCP handshake deadline
                .readTimeout(Duration.ofSeconds(3))   // Socket read deadline (> TimeLimiter so R4j fires first)
                .build();
    }
}
