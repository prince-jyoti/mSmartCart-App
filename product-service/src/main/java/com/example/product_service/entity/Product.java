package com.example.product_service.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotNull
    private String title;

    private String description;

    private String image;

    @Column(nullable = false, precision = 12, scale = 2) // numeric(12,2): exact money, never a float
    private BigDecimal price;

    private int stock;

    private String brand;
    private String model;
    private String color;
    private String category;
    private int discount;
}
