package com.example.payment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String email;
    private String name;
    private String role;
    private List<Long> orderIds;
    private int cartId;
    private List<Long> productIds;

}
