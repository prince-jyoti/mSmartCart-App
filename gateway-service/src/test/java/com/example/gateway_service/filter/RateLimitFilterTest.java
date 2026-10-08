package com.example.gateway_service.filter;

import com.example.gateway_service.configuration.RateLimitProperties;
import com.example.gateway_service.configuration.RateLimitProperties.KeyType;
import com.example.gateway_service.configuration.RateLimitProperties.Rule;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {
    private final AtomicInteger forwarded = new AtomicInteger();
    private final GatewayFilterChain chain = exchange -> {
        forwarded.incrementAndGet();
        return Mono.empty();
    };

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, List.of(
            new Rule("login", "POST", "/auth/login", KeyType.IP, 2, Duration.ofMinutes(1)),
            new Rule("payments", "POST", "/payments/**", KeyType.USER, 1, Duration.ofMinutes(1)))));

    @Test
    void requestsOverTheLimitGet429WithRetryAfterAndAreNotForwarded() {
        assertThat(login("10.0.0.1").getResponse().getStatusCode()).isNull(); // forwarded
        login("10.0.0.1");
        MockServerWebExchange third = login("10.0.0.1");

        assertThat(third.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(Long.parseLong(third.getResponse().getHeaders().getFirst("Retry-After"))).isBetween(1L, 60L);
        assertThat(third.getResponse().getBodyAsString().block()).contains("\"status\":429").contains("Too many requests");
        assertThat(forwarded).hasValue(2);
    }

    @Test
    void eachClientHasItsOwnBucket() {
        login("10.0.0.1");
        login("10.0.0.1");

        assertThat(login("10.0.0.2").getResponse().getStatusCode()).isNull();
        assertThat(forwarded).hasValue(3);
    }

    @Test
    void routesWithoutARuleAndOtherMethodsAreNotLimited() {
        for (int i = 0; i < 5; i++) {
            run(MockServerWebExchange.from(MockServerHttpRequest.get("/products").remoteAddress(addr("10.0.0.1"))));
            run(MockServerWebExchange.from(MockServerHttpRequest.get("/auth/login").remoteAddress(addr("10.0.0.1"))));
        }
        assertThat(forwarded).hasValue(10);
    }

    @Test
    void userRulesCountPerUserNotPerAddress() {
        // Two users behind the same address each get their own allowance.
        assertThat(pay("1", "10.0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(pay("2", "10.0.0.1").getResponse().getStatusCode()).isNull();
        assertThat(pay("1", "10.0.0.9").getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void disabledLetsEverythingThrough() {
        RateLimitFilter off = new RateLimitFilter(new RateLimitProperties(false, List.of(
                new Rule("login", "POST", "/auth/login", KeyType.IP, 1, Duration.ofMinutes(1)))));
        for (int i = 0; i < 3; i++) {
            off.filter(MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login").remoteAddress(addr("10.0.0.1"))), chain).block();
        }
        assertThat(forwarded).hasValue(3);
    }

    private MockServerWebExchange login(String ip) {
        return run(MockServerWebExchange.from(MockServerHttpRequest.post("/auth/login").remoteAddress(addr(ip))));
    }

    private MockServerWebExchange pay(String userId, String ip) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(userId).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/payments").remoteAddress(addr(ip)));
        // As Spring Security does once the token is validated; the response stays shared.
        filter.filter(exchange.mutate().principal(Mono.just(new JwtAuthenticationToken(jwt))).build(), chain).block();
        return exchange;
    }

    private MockServerWebExchange run(MockServerWebExchange exchange) {
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private static InetSocketAddress addr(String ip) {
        return new InetSocketAddress(ip, 40000);
    }
}
