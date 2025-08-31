package com.example.order_service.dto;

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

    private double price;

    private int stock;

    private String brand;
    private String model;
    private String color;
    private String category;
    private int discount;

//    private boolean active;

    private String userId;
}
