package com.example.payment_service.configuration;

import com.example.payment_service.exception.NotFoundException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerConfiguration {

    // A 404, 400 or 409 from another service is a normal answer about the request, not a sign that
    // service is failing. Without this, a run of lookups for missing ids could open the circuit
    // and turn every call into a 503 for a minute. The fallback still maps them to 404/400.
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> ignoreClientErrors() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .ignoreExceptions(NotFoundException.class, IllegalArgumentException.class, IllegalStateException.class)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.ofDefaults())
                .build());
    }
}
