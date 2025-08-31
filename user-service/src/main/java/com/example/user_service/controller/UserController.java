package com.example.user_service.controller;

import com.example.user_service.dto.UserDto;
import com.example.user_service.entity.User;
import com.example.user_service.service.UserService;
import com.example.user_service.utils.BaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<BaseResponse<UserDto>> createOrUpdate(@AuthenticationPrincipal Jwt jwt) {
        String keycloakUserId = jwt.getSubject(); // 'sub' claim
        if (keycloakUserId == null || keycloakUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse<>(400, "Invalid token: missing subject", null));
        }
        log.info("Processing user with Keycloak ID: {}", keycloakUserId);
        User user = new User();
        user.setKeycloakUserId(keycloakUserId);
        try {
            UserDto result = userService.createOrUpdateUser(user);
            return ResponseEntity.ok(new BaseResponse<>(200, "User found", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, e.getMessage(), null));
        }
    }
}
