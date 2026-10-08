package com.example.gateway_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

// rate-limit.* in application.yml: which requests are limited, per what, and how much.
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(boolean enabled, List<Rule> rules) {

    public RateLimitProperties {
        rules = rules == null ? List.of() : rules;
    }

    // Allows `capacity` requests per `period` (refilled gradually) for each key.
    public record Rule(String name, String method, String path, KeyType key, int capacity, Duration period) {
    }

    public enum KeyType {
        IP,   // the client's address (for anonymous endpoints such as login)
        USER  // the token's subject (user id); falls back to IP without a token
    }
}
