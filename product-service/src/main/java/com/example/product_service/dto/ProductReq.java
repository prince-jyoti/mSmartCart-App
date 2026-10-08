package com.example.product_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductReq {
    private Long id;

    @NotBlank(message = "Title is required")

    private String title;

    private String description;

    private String image;

    @NotNull(message = "Price is required")

    @Positive(message = "Price must be greater than 0")

    @Digits(integer = 10, fraction = 2, message = "Price can have at most 2 decimal places")

    private BigDecimal price;

    @PositiveOrZero(message = "Stock cannot be negative")

    private int stock;

    private String brand;
    private String model;
    private String color;
    private String category;
    @Min(value = 0, message = "Discount must be 0-100") @Max(value = 100, message = "Discount must be 0-100")
    private int discount;
}
