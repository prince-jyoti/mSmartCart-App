package com.example.product_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Sent by order-service when an order is placed.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReservationReq {
    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotEmpty(message = "At least one item is required") @Valid
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull(message = "productId is required")
        private Long productId;

        @Positive(message = "Quantity must be at least 1")
        private int quantity;
    }
}
