package com.example.gateway_service.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;



@EnableWebFluxSecurity
@Configuration
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
//                .cors(Customizer.withDefaults())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Allow all OPTIONS requests
                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/products/**", "/user/**","/carts/**","/orders/**","/payments/**").authenticated()
                        .anyExchange().permitAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(reactiveJwtDecoder()))
                        .bearerTokenConverter(bearerTokenConverter())
                );

        return http.build();
    }

    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    public ServerAuthenticationConverter bearerTokenConverter() {
        return new CookieServerBearerTokenResolver();
    }

}
