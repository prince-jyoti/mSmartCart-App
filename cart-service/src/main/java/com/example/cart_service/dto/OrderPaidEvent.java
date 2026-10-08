package com.example.cart_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// Published by order-service once an order is PAID.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {
    private String eventId;
    private String orderId;
    private Long userId;
    private List<Item> items;
    private LocalDateTime occurredAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private int quantity;
    }
}
