package com.example.user_service.service;

import com.example.user_service.dto.AuthReq;
import com.example.user_service.dto.AuthRes;
import com.example.user_service.entity.User;
import com.example.user_service.repository.UserRepo;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class AuthService {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final Duration TOKEN_TTL = Duration.ofMinutes(90);

    public AuthRes register(AuthReq req) {
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }
        User user = new User();
        user.setEmail(req.getEmail());
        user.setName(req.getName());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setRole("ADMIN".equalsIgnoreCase(req.getRole()) ? "ADMIN" : "USER");
        User saved = userRepo.save(user);
        return toAuthRes(saved);
    }

    public User login(AuthReq req) {
        User user = userRepo.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        return user;
    }

    public String issueToken(User user) {
        // Explicit HS256 key: NimbusJwtDecoder.withSecretKey(...) on the resource-server
        // side defaults to expecting HS256, so the signing side must match exactly
        // rather than let jjwt auto-pick a stronger algorithm (e.g. HS384) based on key length.
        SecretKey key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Date now = new Date();
        Date expiry = new Date(now.getTime() + TOKEN_TTL.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getTokenTtlSeconds() {
        return TOKEN_TTL.getSeconds();
    }

    public AuthRes toAuthRes(User user) {
        return new AuthRes(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
