package com.example.order_service.dto;

import java.time.Instant;
import java.math.BigDecimal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class OrderReq {
    private Long id;
    private String orderId;
    private Long userId;
    @NotEmpty(message = "Order must contain at least one item") @Valid
    private List<OrderItemDTO> items;
    private BigDecimal totalAmount; // ignored: the server computes it
    private String status;
    private Instant createdAt; // ignored: set by the server
    private Long paymentId;
}