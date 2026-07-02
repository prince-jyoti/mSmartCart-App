package com.example.user_service.controller;

import com.example.user_service.dto.UserDto;
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

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<UserDto>> me(@AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new BaseResponse<>(400, "Invalid token: missing email claim", null));
        }
        log.info("Fetching profile for user: {}", email);
        try {
            UserDto result = userService.getByEmail(email);
            return ResponseEntity.ok(new BaseResponse<>(200, "User found", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new BaseResponse<>(500, e.getMessage(), null));
        }
    }

    // Used by other services (e.g. order-service) to resolve the actual
    // owner of a resource by internal id, as opposed to /me which always
    // resolves the caller making the request.
    @GetMapping("/{id}")
    public ResponseEntity<BaseResponse<UserDto>> getById(@PathVariable Long id) {
        try {
            UserDto result = userService.getById(id);
            return ResponseEntity.ok(new BaseResponse<>(200, "User found", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new BaseResponse<>(404, e.getMessage(), null));
        }
    }
}
