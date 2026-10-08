package com.example.cart_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class CartItemDTO {
    private Long id;
    private Long cartId; // Replaces direct Cart entity reference
    @NotNull(message = "productId is required")
    private Long productId; // Replaces direct Product entity reference
    @Positive(message = "Quantity must be at least 1")
    private int quantity=1; // Default quantity set to 1
    private BigDecimal total; // price x quantity

    private String title;

    private String description;

    private String image;

    private BigDecimal price;

    private String brand;
    private String model;
    private String color;
    private String category;
    private int discount;
}