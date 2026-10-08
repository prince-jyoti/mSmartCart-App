package com.example.user_service.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthReq {
    private String name;
    @NotBlank(message = "Email is required") @Email(message = "Email is invalid")
    private String email;
    @NotBlank(message = "Password is required")
    private String password;
    private String role;
}
