package com.example.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// Published on order.paid (cart-service removes the bought items, product-service confirms the
// stock reservation) and order.expired (product-service releases the reservation).
// Consumers dedupe on eventId.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    private String eventId;
    private String orderId;   // public order id (ORD-...)
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
