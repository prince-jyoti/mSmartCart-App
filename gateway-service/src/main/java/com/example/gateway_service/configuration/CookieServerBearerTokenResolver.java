package com.example.gateway_service.configuration;

import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.BearerTokenAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class CookieServerBearerTokenResolver implements ServerAuthenticationConverter {
    private static final String COOKIE_NAME = "auth_token";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
        if (cookie == null) {
            return Mono.empty();
        }
        return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
    }
}
