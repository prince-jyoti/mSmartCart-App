package com.example.order_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderItemDTO {
    private Long id;

    @NotNull(message = "productId is required")

    private Long productId;

    private String title;

    private String image;

    @Positive(message = "Quantity must be at least 1")

    private int quantity;

    private BigDecimal price;
}
