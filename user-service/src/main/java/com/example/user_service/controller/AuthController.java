package com.example.user_service.controller;

import com.example.user_service.dto.AuthReq;
import com.example.user_service.dto.AuthRes;
import com.example.user_service.entity.User;
import com.example.user_service.service.AuthService;
import com.example.user_service.utils.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String COOKIE_NAME = "auth_token";

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<AuthRes>> register(@RequestBody AuthReq req) {
        try {
            AuthRes result = authService.register(req);
            return ResponseEntity.ok(new BaseResponse<>(200, "Registered successfully", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse<>(400, e.getMessage(), null));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthRes>> login(@RequestBody AuthReq req) {
        try {
            User user = authService.login(req);
            String token = authService.issueToken(user);
            ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(authService.getTokenTtlSeconds())
                    .build();
            return ResponseEntity.ok()
                    .header("Set-Cookie", cookie.toString())
                    .body(new BaseResponse<>(200, "Login successful", authService.toAuthRes(user)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new BaseResponse<>(401, e.getMessage(), null));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout() {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(new BaseResponse<>(200, "Logged out", null));
    }
}
