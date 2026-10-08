package com.example.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Body of product-service's POST /internal/stock/reservations.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockReservationReq {
    private String orderId;
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private int quantity;
    }
}
