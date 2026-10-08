package com.example.cart_service.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ProductDTO {
    private Long id;

    private String title;

    private String description;

    private String image;

    private BigDecimal price;

    private int stock;

    private String brand;
    private String model;
    private String color;
    private String category;
    private int discount;
}
