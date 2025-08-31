package com.example.cart_service.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class CartItemDTO {
    private Long id;
    private Long cartId; // Replaces direct Cart entity reference
    private Long productId; // Replaces direct Product entity reference
    private int quantity=1; // Default quantity set to 1
    private double total; // Calculated field

    private String title;

    private String description;

    private String image;

    private double price;


    private String brand;
    private String model;
    private String color;
    private String category;
    private int discount;
}