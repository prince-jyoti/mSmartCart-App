package com.example.order_service.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    // Each task runs with the SecurityContext of the thread that submitted it, so the
    // Feign calls made from OrderService.toOrderRes still carry the caller's JWT.
    @Bean(destroyMethod = "shutdown")
    public ExecutorService orderEnrichmentExecutor() {
        return new DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(10));
    }
}
