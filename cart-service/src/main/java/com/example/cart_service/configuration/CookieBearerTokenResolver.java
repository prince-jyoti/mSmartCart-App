package com.example.cart_service.configuration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class CookieBearerTokenResolver implements BearerTokenResolver {
    private static final String COOKIE_NAME = "auth_token";
    // Falls back to the standard Authorization header resolver so
    // service-to-service Feign calls (which propagate the JWT via
    // "Authorization: Bearer <token>", not a cookie) keep working.
    private final BearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return headerResolver.resolve(request);
    }
}
