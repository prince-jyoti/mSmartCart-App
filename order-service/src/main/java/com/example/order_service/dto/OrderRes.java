package com.example.order_service.dto;

import java.time.Instant;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderRes {
    private Long id;
    private String orderId;
    private UserDTO  user;
    private List<OrderItemDTO> items;
    private BigDecimal totalAmount;
    private String status;
    private Instant createdAt;
    private PaymentRes payment;
    // Seconds until an unpaid order (PENDING / PAYMENT_FAILED) expires; null otherwise. Relative,
    // so the client needs no clock or timezone agreement with the server.
    private Long expiresInSeconds;
}
