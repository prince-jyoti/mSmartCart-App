package com.example.payment_service.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderItemDTO {
    private Long id;

    private Long productId;

    private String title;

    private String image;

    private int quantity;

    private BigDecimal price;
}
