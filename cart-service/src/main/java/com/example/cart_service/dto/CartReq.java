package com.example.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;



@AllArgsConstructor
@Data
@NoArgsConstructor
public class CartReq {
    private Long id;
    private Long userId; // Replaces direct User entity reference
    private List<CartItemDTO> items; // Replaces direct CartItem entity reference
    private double totalPrice;
}