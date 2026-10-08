package com.example.gateway_service.filter;

import com.example.gateway_service.configuration.RateLimitProperties;
import com.example.gateway_service.configuration.RateLimitProperties.KeyType;
import com.example.gateway_service.configuration.RateLimitProperties.Rule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

// Token-bucket rate limiting for the routes in rate-limit.rules (login, sign-up, placing orders,
// payments). Buckets live in this gateway's memory: fine for a single gateway; with several,
// each would count separately and a shared store (e.g. Redis) would be needed.
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final boolean enabled;
    private final List<CompiledRule> rules;
    // One bucket per (rule, key). Bounded and expiring so many distinct clients can't exhaust memory.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofHours(1))
            .build();

    public RateLimitFilter(RateLimitProperties properties) {
        this.enabled = properties.enabled();
        this.rules = properties.rules().stream().map(CompiledRule::new).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        Optional<CompiledRule> rule = rules.stream().filter(r -> r.matches(exchange)).findFirst();
        if (rule.isEmpty()) {
            return chain.filter(exchange);
        }
        return keyFor(rule.get(), exchange).flatMap(key -> {
            Bucket bucket = buckets.get(rule.get().rule.name() + ":" + key, k -> rule.get().newBucket());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
                return chain.filter(exchange);
            }
            long retryAfter = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
            log.warn("Rate limit '{}' exceeded for {} on {} {}", rule.get().rule.name(), key,
                    exchange.getRequest().getMethod(), exchange.getRequest().getPath());
            return tooManyRequests(exchange, retryAfter);
        });
    }

    // Runs before routing; Spring Security has already authenticated the request by then.
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 10;
    }

    private Mono<String> keyFor(CompiledRule rule, ServerWebExchange exchange) {
        String ip = "ip:" + clientIp(exchange);
        if (rule.rule.key() != KeyType.USER) {
            return Mono.just(ip);
        }
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(p -> "user:" + ((JwtAuthenticationToken) p).getToken().getSubject())
                .defaultIfEmpty(ip);
    }

    // The TCP peer address. Behind a reverse proxy this would be the proxy; then use its
    // X-Forwarded-For, but only when the proxy is trusted (clients can forge the header).
    private static String clientIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    private static Mono<Void> tooManyRequests(ServerWebExchange exchange, long retryAfterSeconds) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // Same body shape as the services' errors, so the frontend shows the message as usual.
        String body = "{\"status\":429,\"message\":\"Too many requests. Please try again in "
                + retryAfterSeconds + " seconds.\",\"data\":null}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static final class CompiledRule {
        private final Rule rule;
        private final HttpMethod method;
        private final PathPattern path;

        CompiledRule(Rule rule) {
            this.rule = rule;
            this.method = rule.method() == null ? null : HttpMethod.valueOf(rule.method().toUpperCase());
            this.path = PathPatternParser.defaultInstance.parse(rule.path());
        }

        boolean matches(ServerWebExchange exchange) {
            return (method == null || method.equals(exchange.getRequest().getMethod()))
                    && path.matches(PathContainer.parsePath(exchange.getRequest().getPath().value()));
        }

        Bucket newBucket() {
            return Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(rule.capacity()).refillGreedy(rule.capacity(), rule.period()).build())
                    .build();
        }
    }
}
